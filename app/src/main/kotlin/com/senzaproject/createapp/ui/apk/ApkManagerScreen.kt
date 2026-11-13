package com.senzaproject.createapp.ui.apk

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.senzaproject.createapp.R
import com.senzaproject.createapp.apk.ApkIntents
import com.senzaproject.createapp.domain.builder.ArtifactEntry
import com.senzaproject.createapp.domain.builder.ArtifactHistoryItem
import com.senzaproject.createapp.domain.builder.ArtifactKind
import com.senzaproject.createapp.domain.builder.ArtifactSnapshot
import com.senzaproject.createapp.domain.builder.ArtifactValidation
import com.senzaproject.createapp.domain.builder.InstallBlock
import com.senzaproject.createapp.ui.builder.formatSize
import com.senzaproject.createapp.ui.builder.labelRes
import com.senzaproject.createapp.ui.common.messageRes
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.ConfirmDestructiveDialog
import com.senzaproject.createapp.ui.components.ContentContainer
import com.senzaproject.createapp.ui.components.EmptyState
import com.senzaproject.createapp.ui.components.KeyValueRow
import com.senzaproject.createapp.ui.components.SectionCard
import com.senzaproject.createapp.ui.components.StatusChip
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

    // File bisa berubah di luar aplikasi (build di Termux, dihapus lewat aplikasi File): baca ulang tiap kembali.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, context.getString(message.textRes()), Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    val error = viewModel.errorMessage
    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, context.getString(error.messageRes()), Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }

    val launch = viewModel.launch
    LaunchedEffect(launch) {
        if (launch != null) {
            when (launch) {
                is ApkLaunch.Install -> {
                    if (ApkIntents.canRequestInstalls(context)) {
                        startSafely(context, ApkIntents.install(launch.uri)) { viewModel.onNoAppForAction() }
                    } else {
                        // Izin "pasang aplikasi tidak dikenal" wajib dari pengguna; arahkan ke pengaturannya.
                        viewModel.onNeedInstallPermission()
                        startSafely(context, ApkIntents.unknownSourcesSettings(context)) { viewModel.onNoAppForAction() }
                    }
                }

                is ApkLaunch.Share -> {
                    startSafely(context, ApkIntents.share(launch.uri, launch.mimeType, launch.title)) {
                        viewModel.onNoAppForAction()
                    }
                }
            }
            viewModel.consumeLaunch()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? -> viewModel.finishExport(uri) }
    val exportRequest = viewModel.exportRequest
    LaunchedEffect(exportRequest) {
        if (exportRequest != null && !exportRequest.launched) {
            exportRequest.launched = true
            try {
                exportLauncher.launch(exportRequest.suggestedName)
            } catch (e: ActivityNotFoundException) {
                viewModel.finishExport(null)
                viewModel.onNoAppForAction()
            }
        }
    }

    val snapshot = viewModel.snapshot
    val busy = viewModel.busy

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.apk_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::refresh, enabled = !viewModel.loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.apk_refresh))
                }
            },
        )
        ContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (busy != null) {
                    item { BusyCard(busy = busy, onCancel = viewModel::cancelBusy) }
                }
                when {
                    viewModel.sharedPath == null -> item {
                        EmptyState(
                            icon = Icons.Filled.Android,
                            title = stringResource(R.string.apk_no_folder_title),
                            description = stringResource(R.string.apk_no_folder_desc),
                        )
                    }

                    viewModel.loadError != null -> item {
                        SectionCard(title = stringResource(R.string.apk_title)) {
                            Text(
                                text = stringResource(R.string.apk_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = viewModel::refresh) { Text(stringResource(R.string.apk_retry)) }
                        }
                    }

                    snapshot == null -> item {
                        SectionCard(title = stringResource(R.string.apk_title)) {
                            Text(stringResource(R.string.apk_loading), style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    else -> {
                        item { SummaryCard(snapshot = snapshot, folder = viewModel.sharedPath.orEmpty()) }
                        if (snapshot.entries.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Filled.Android,
                                    title = stringResource(R.string.apk_no_files_title),
                                    description = stringResource(R.string.apk_no_files_desc),
                                )
                            }
                        }
                        items(snapshot.entries, key = { it.key }) { entry ->
                            ArtifactCard(
                                entry = entry,
                                outputPath = viewModel.outputPath(entry),
                                enabled = busy == null,
                                onInstall = { viewModel.install(entry) },
                                onShare = { viewModel.share(entry) },
                                onExport = { viewModel.requestExport(entry) },
                                onDetails = { viewModel.showDetails(entry) },
                                onOpenFolder = {
                                    val id = viewModel.outputDocumentId(entry)
                                    val path = viewModel.outputPath(entry)
                                    val opened = id != null &&
                                        startSafely(context, ApkIntents.openFolder(id)) { }
                                    if (!opened && path != null) {
                                        // Tidak ada aplikasi File yang bisa membuka folder: setidaknya salin path-nya.
                                        copyToClipboard(context, path)
                                        viewModel.onPathCopied()
                                    }
                                },
                                onCopyPath = {
                                    viewModel.outputPath(entry)?.let { path ->
                                        copyToClipboard(context, path)
                                        viewModel.onPathCopied()
                                    }
                                },
                                onDelete = { viewModel.requestDelete(entry) },
                            )
                        }
                        item { HistoryCard(items = snapshot.history) }
                    }
                }
            }
        }
    }

    val pending = viewModel.pendingDelete
    if (pending != null) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.apk_delete_title),
            message = stringResource(R.string.apk_delete_message, pending.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    val details = viewModel.details
    if (details != null) {
        DetailsDialog(details = details, outputPath = viewModel.outputPath(details.entry), onClose = viewModel::closeDetails)
    }
}

@Composable
private fun SummaryCard(snapshot: ArtifactSnapshot, folder: String) {
    val summary = snapshot.summary
    SectionCard(title = stringResource(R.string.apk_summary_title)) {
        Text(
            text = stringResource(R.string.apk_summary_count, summary.count, formatSize(summary.totalBytes)),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.apk_summary_folder, folder),
            style = CodeTextStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BusyCard(busy: ApkBusy, onCancel: () -> Unit) {
    SectionCard(title = stringResource(busy.kind.labelRes())) {
        Text(busy.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = stringResource(R.string.apk_busy_progress, formatSize(busy.doneBytes), formatSize(busy.totalBytes)),
            style = MaterialTheme.typography.bodySmall,
        )
        LinearProgressIndicator(progress = { busy.fraction }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onCancel) { Text(stringResource(R.string.apk_busy_cancel)) }
    }
}

@Composable
private fun ArtifactCard(
    entry: ArtifactEntry,
    outputPath: String?,
    enabled: Boolean,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDetails: () -> Unit,
    onOpenFolder: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val unknown = stringResource(R.string.apk_unknown)
    SectionCard(title = entry.name) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = entry.kind.name, containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer)
            val colors = validationColors(entry.validation)
            StatusChip(
                text = stringResource(entry.validation.labelRes()),
                containerColor = colors.first,
                contentColor = colors.second,
            )
            if (entry.isUnsigned) {
                StatusChip(
                    text = stringResource(R.string.apk_unsigned_chip),
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                )
            }
        }

        KeyValueRow(label = stringResource(R.string.apk_field_size), value = formatSize(entry.sizeBytes))
        KeyValueRow(label = stringResource(R.string.apk_field_project), value = entry.projectName ?: unknown)
        val build = entry.build
        if (build != null) {
            KeyValueRow(label = stringResource(R.string.apk_field_type), value = stringResource(build.type.labelRes()))
        }
        KeyValueRow(label = stringResource(R.string.apk_field_built), value = formatStamp(entry.builtAtMillis, unknown))
        if (outputPath != null) {
            Text(text = outputPath, style = CodeTextStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        val block = entry.installBlock
        if (block != null) {
            val checking = block == InstallBlock.NOT_CHECKED
            Text(
                text = stringResource(block.reasonRes()),
                style = MaterialTheme.typography.bodySmall,
                color = if (checking) scheme.onSurfaceVariant else scheme.error,
            )
            if (checking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        var menuOpen by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onInstall, enabled = enabled && entry.kind == ArtifactKind.APK) {
                Text(stringResource(R.string.apk_action_install))
            }
            OutlinedButton(onClick = onShare, enabled = enabled && entry.canShare) {
                Text(stringResource(R.string.apk_action_share))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.apk_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.apk_action_export)) },
                        enabled = enabled && entry.canShare,
                        onClick = {
                            menuOpen = false
                            onExport()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.apk_action_details)) },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onDetails()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.apk_action_open_folder)) },
                        enabled = outputPath != null,
                        onClick = {
                            menuOpen = false
                            onOpenFolder()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.apk_action_copy_path)) },
                        enabled = outputPath != null,
                        onClick = {
                            menuOpen = false
                            onCopyPath()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.apk_action_delete), color = scheme.error) },
                        enabled = enabled,
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(items: List<ArtifactHistoryItem>) {
    SectionCard(title = stringResource(R.string.apk_history_title)) {
        if (items.isEmpty()) {
            Text(stringResource(R.string.apk_history_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            items.take(MAX_HISTORY_SHOWN).forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.record.projectName + " • " + stringResource(item.record.type.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatStamp(item.record.startedAtMillis, ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    item.artifacts.forEach { artifact ->
                        Text(
                            text = artifact.name + " — " + stringResource(
                                if (artifact.available) R.string.apk_history_available else R.string.apk_history_gone,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (artifact.available) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsDialog(details: ApkDetails, outputPath: String?, onClose: () -> Unit) {
    val entry = details.entry
    val info = details.info
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.apk_details_title, entry.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyValueRow(label = stringResource(R.string.apk_field_size), value = formatSize(entry.sizeBytes))
                if (info != null) {
                    KeyValueRow(label = stringResource(R.string.apk_detail_label), value = info.label ?: "-")
                    KeyValueRow(label = stringResource(R.string.apk_detail_package), value = info.packageName)
                    KeyValueRow(
                        label = stringResource(R.string.apk_detail_version),
                        value = (info.versionName ?: "-") + " (" + info.versionCode + ")",
                    )
                    KeyValueRow(
                        label = stringResource(R.string.apk_detail_sdk),
                        value = info.minSdk.toString() + " / " + info.targetSdk,
                    )
                } else if (entry.kind == ArtifactKind.AAB) {
                    Text(stringResource(R.string.apk_detail_aab_note), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.apk_detail_unreadable), style = MaterialTheme.typography.bodySmall)
                }
                if (outputPath != null) {
                    Text(text = outputPath, style = CodeTextStyle)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.bld_close)) }
        },
    )
}

private const val MAX_HISTORY_SHOWN = 10

@Composable
private fun validationColors(validation: ArtifactValidation): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (validation) {
        ArtifactValidation.VALID -> Pair(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ArtifactValidation.INVALID, ArtifactValidation.MISSING -> Pair(scheme.errorContainer, scheme.onErrorContainer)
        ArtifactValidation.NOT_CHECKED -> Pair(scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

private fun formatStamp(millis: Long, fallback: String): String =
    if (millis <= 0L) fallback else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/** Menjalankan [intent]; false (dan [onMissing]) bila tidak ada aplikasi yang bisa menanganinya. */
private fun startSafely(context: Context, intent: Intent, onMissing: () -> Unit): Boolean {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        onMissing()
        false
    } catch (e: SecurityException) {
        onMissing()
        false
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Create App", text))
}
