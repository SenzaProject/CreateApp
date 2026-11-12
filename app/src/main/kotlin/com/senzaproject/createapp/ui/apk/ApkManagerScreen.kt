package com.senzaproject.createapp.ui.apk

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.apk.ArtifactAvailability
import com.senzaproject.createapp.domain.apk.ArtifactNames
import com.senzaproject.createapp.domain.apk.HistoryEntry
import com.senzaproject.createapp.domain.apk.InstallBlock
import com.senzaproject.createapp.domain.apk.InstallPolicy
import com.senzaproject.createapp.domain.apk.ManagedArtifact
import com.senzaproject.createapp.domain.builder.ArtifactValidation
import com.senzaproject.createapp.domain.builder.SharedStoragePath
import com.senzaproject.createapp.domain.builder.WorkspaceLayout
import com.senzaproject.createapp.ui.builder.formatElapsed
import com.senzaproject.createapp.ui.builder.formatSize
import com.senzaproject.createapp.ui.builder.labelRes
import com.senzaproject.createapp.ui.common.messageRes
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.ContentContainer
import com.senzaproject.createapp.ui.components.EmptyState
import com.senzaproject.createapp.ui.components.SectionCard
import com.senzaproject.createapp.ui.theme.CodeTextStyle
import java.text.DateFormat
import java.util.Date

@Composable
fun ApkManagerScreen(
    viewModel: ApkManagerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }

    // Isi folder bisa berubah di luar aplikasi (build selesai, file dihapus di aplikasi File).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    var exportTarget by remember { mutableStateOf<ManagedArtifact?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val artifact = exportTarget
        exportTarget = null
        if (uri != null && artifact != null) viewModel.export(artifact, uri.toString())
    }

    val event = viewModel.event
    LaunchedEffect(event) {
        if (event != null) {
            handleEvent(context, event)
            viewModel.consumeEvent()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.apk_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.apk_refresh))
                }
            },
        )
        ContentContainer {
            Column(modifier = Modifier.fillMaxSize()) {
                if (viewModel.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.apk_tab_files)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.apk_tab_history)) })
                }
                if (tab == 0) {
                    FilesTab(viewModel = viewModel)
                } else {
                    HistoryTab(viewModel = viewModel)
                }
            }
        }
    }

    val selected = viewModel.selected
    if (selected != null) {
        DetailDialog(
            viewModel = viewModel,
            artifact = selected,
            onExport = {
                exportTarget = selected
                try {
                    exportLauncher.launch(selected.name)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.apk_msg_picker_unavailable, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    val pending = viewModel.pendingDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.apk_delete_title)) },
            text = { Text(stringResource(R.string.apk_delete_message, pending.name, formatSize(pending.sizeBytes))) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.apk_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

// --- Tab File ---

@Composable
private fun FilesTab(viewModel: ApkManagerViewModel) {
    val groups = viewModel.groups
    val error = viewModel.listError
    when {
        viewModel.treeUri == null -> EmptyState(
            icon = Icons.Filled.Refresh,
            title = stringResource(R.string.apk_need_folder_title),
            description = stringResource(R.string.apk_need_folder_desc),
        )

        error != null -> EmptyState(
            icon = Icons.Filled.Refresh,
            title = stringResource(R.string.apk_list_failed),
            description = stringResource(error.messageRes()),
        )

        groups.isEmpty() && !viewModel.loading -> EmptyState(
            icon = Icons.Filled.Refresh,
            title = stringResource(R.string.apk_empty_title),
            description = stringResource(R.string.apk_empty_desc),
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(groups, key = { it.projectId }) { group ->
                SectionCard(title = viewModel.projectName(group.projectId)) {
                    Text(
                        stringResource(R.string.apk_group_summary, group.artifacts.size, formatSize(group.totalBytes)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    group.artifacts.forEach { artifact ->
                        ArtifactRow(artifact = artifact, busy = viewModel.busyKey == artifact.key) {
                            viewModel.open(artifact)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactRow(artifact: ManagedArtifact, busy: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(artifact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(
            artifact.kind.name + " • " + formatSize(artifact.sizeBytes) + " • " + formatStamp(artifact.modifiedMillis),
            style = MaterialTheme.typography.bodySmall,
        )
        if (artifact.looksUnsigned) {
            Text(
                stringResource(R.string.bld_artifact_unsigned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

// --- Tab Riwayat ---

@Composable
private fun HistoryTab(viewModel: ApkManagerViewModel) {
    val entries = viewModel.historyEntries
    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Refresh,
            title = stringResource(R.string.bld_history_empty),
            description = stringResource(R.string.apk_history_desc),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.record.id }) { entry -> HistoryRow(entry) }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val record = entry.record
    SectionCard(title = record.projectName + " • " + stringResource(record.type.labelRes())) {
        Text(
            stringResource(record.status.labelRes()) + " • " + formatStamp(record.startedAtMillis) + " • " +
                formatElapsed(record.durationMillis),
            style = MaterialTheme.typography.bodySmall,
        )
        entry.artifacts.forEach { item ->
            val label = when (item.availability) {
                ArtifactAvailability.AVAILABLE -> R.string.apk_hist_available
                ArtifactAvailability.REPLACED -> R.string.apk_hist_replaced
                ArtifactAvailability.DELETED -> R.string.apk_hist_deleted
            }
            Text(item.name + " — " + stringResource(label), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// --- Detail ---

@Composable
private fun DetailDialog(viewModel: ApkManagerViewModel, artifact: ManagedArtifact, onExport: () -> Unit) {
    val context = LocalContext.current
    val details = viewModel.selectedDetails
    val busy = viewModel.busyKey == artifact.key
    val rootPath = viewModel.sharedPath
    val path = rootPath?.let { artifact.path(it) }
    val canInstall = {
        context.packageManager.canRequestPackageInstalls()
    }

    AlertDialog(
        onDismissRequest = viewModel::closeDetails,
        title = { Text(artifact.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (viewModel.detailsLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Line(R.string.apk_d_project, viewModel.projectName(artifact.projectId))
                Line(R.string.apk_d_type, artifact.kind.name)
                Line(R.string.apk_d_size, formatSize(artifact.sizeBytes) + " (" + artifact.sizeBytes + " B)")
                Line(R.string.apk_d_modified, formatStamp(artifact.modifiedMillis))
                if (path != null) Line(R.string.apk_d_path, path, code = true)
                if (details != null) {
                    val validation = when (details.validation) {
                        ArtifactValidation.VALID -> R.string.bld_artifact_valid
                        ArtifactValidation.INVALID -> R.string.bld_artifact_invalid
                        ArtifactValidation.MISSING -> R.string.bld_artifact_missing
                        ArtifactValidation.NOT_CHECKED -> R.string.bld_artifact_unchecked
                    }
                    Line(R.string.apk_d_validation, stringResource(validation))
                    details.sha256?.let { Line(R.string.apk_d_sha, it, code = true) }
                    details.apk?.let { apk ->
                        Line(R.string.apk_d_package, apk.packageName, code = true)
                        apk.label?.let { Line(R.string.apk_d_label, it) }
                        Line(R.string.apk_d_version, (apk.versionName ?: "-") + " (" + apk.versionCode + ")")
                        Line(R.string.apk_d_sdk, "min " + (apk.minSdk ?: "-") + ", target " + (apk.targetSdk ?: "-"))
                        val signers = apk.signerCount
                        val signed = when {
                            signers == null -> stringResource(R.string.apk_sign_unknown)
                            signers > 0 -> stringResource(R.string.apk_sign_present, signers)
                            else -> stringResource(R.string.apk_sign_none)
                        }
                        Line(R.string.apk_d_signature, signed)
                    }
                    if (artifact.kind == com.senzaproject.createapp.domain.builder.ArtifactKind.AAB) {
                        Text(stringResource(R.string.apk_aab_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    val unsigned = InstallPolicy.isUnsigned(artifact, details)
                    if (unsigned && artifact.kind == com.senzaproject.createapp.domain.builder.ArtifactKind.APK) {
                        Text(
                            stringResource(R.string.bld_artifact_unsigned),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(stringResource(R.string.apk_sign_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Row2 {
                    OutlinedButton(
                        onClick = { viewModel.install(artifact, canInstall()) },
                        enabled = !busy && artifact.kind == com.senzaproject.createapp.domain.builder.ArtifactKind.APK,
                    ) { Text(stringResource(R.string.apk_install)) }
                    OutlinedButton(onClick = { viewModel.share(artifact) }, enabled = !busy) {
                        Text(stringResource(R.string.apk_share))
                    }
                }
                Row2 {
                    OutlinedButton(onClick = onExport, enabled = !busy) { Text(stringResource(R.string.apk_export)) }
                    OutlinedButton(onClick = { viewModel.askDelete(artifact) }, enabled = !busy) {
                        Text(stringResource(R.string.apk_delete))
                    }
                }
                Row2 {
                    if (path != null) {
                        TextButton(onClick = {
                            copyToClipboard(context, path)
                            Toast.makeText(context, R.string.apk_msg_path_copied, Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.apk_copy_path)) }
                        TextButton(onClick = { openFolder(context, rootPath, artifact) }) {
                            Text(stringResource(R.string.apk_open_folder))
                        }
                    }
                    TextButton(onClick = viewModel::closeDetails) { Text(stringResource(R.string.bld_close)) }
                }
            }
        },
    )
}

@Composable
private fun Row2(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Line(labelRes: Int, value: String, code: Boolean = false) {
    Column {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(value, style = if (code) CodeTextStyle else MaterialTheme.typography.bodyMedium)
    }
}

// --- Aksi sistem ---

private fun handleEvent(context: Context, event: ApkEvent) {
    when (event) {
        is ApkEvent.Install -> launchInstall(context, event.contentUri)
        is ApkEvent.Share -> launchShare(context, event.contentUri, event.mime)
        is ApkEvent.Blocked -> {
            if (event.reason == InstallBlock.NEED_INSTALL_PERMISSION) {
                Toast.makeText(context, R.string.apk_block_permission, Toast.LENGTH_LONG).show()
                openInstallSettings(context)
            } else {
                val text = when (event.reason) {
                    InstallBlock.NOT_APK -> R.string.apk_block_not_apk
                    InstallBlock.INVALID_FILE -> R.string.apk_block_invalid
                    InstallBlock.UNSIGNED -> R.string.apk_block_unsigned
                    InstallBlock.NEED_INSTALL_PERMISSION -> R.string.apk_block_permission
                }
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        }

        is ApkEvent.Failed -> Toast.makeText(context, event.error.messageRes(), Toast.LENGTH_LONG).show()
        ApkEvent.NeedFolder -> Toast.makeText(context, R.string.apk_need_folder_title, Toast.LENGTH_LONG).show()
        is ApkEvent.Exported -> Toast.makeText(
            context,
            context.getString(R.string.apk_msg_exported, formatSize(event.bytes)),
            Toast.LENGTH_LONG,
        ).show()

        ApkEvent.Deleted -> Toast.makeText(context, R.string.apk_msg_deleted, Toast.LENGTH_SHORT).show()
    }
}

private fun launchInstall(context: Context, contentUri: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(contentUri), ArtifactNames.mimeType(com.senzaproject.createapp.domain.builder.ArtifactKind.APK))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.apk_msg_no_installer, Toast.LENGTH_LONG).show()
    }
}

private fun launchShare(context: Context, contentUri: String, mime: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(contentUri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.apk_msg_no_share_target, Toast.LENGTH_LONG).show()
    }
}

private fun openInstallSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.apk_msg_no_installer, Toast.LENGTH_LONG).show()
    }
}

/** Membuka folder keluaran di aplikasi File; bila tidak ada yang bisa, path disalin agar bisa ditempel. */
private fun openFolder(context: Context, rootPath: String?, artifact: ManagedArtifact) {
    if (rootPath == null) return
    val folder = WorkspaceLayout.outputPath(rootPath, artifact.projectId)
    val documentId = SharedStoragePath.documentIdFor(folder)
    if (documentId != null) {
        val uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // jatuh ke penyalinan path
        } catch (e: SecurityException) {
            // jatuh ke penyalinan path
        }
    }
    copyToClipboard(context, folder)
    Toast.makeText(context, R.string.apk_msg_folder_copied, Toast.LENGTH_LONG).show()
}

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

private fun formatStamp(millis: Long): String =
    if (millis <= 0L) "-" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Create App", text))
}
