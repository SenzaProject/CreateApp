package com.senzaproject.createapp.ui.builder

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.createapp.R
import com.senzaproject.createapp.core.terminal.TermuxPaths
import com.senzaproject.createapp.domain.builder.BuildArtifact
import com.senzaproject.createapp.domain.builder.BuildIssue
import com.senzaproject.createapp.domain.builder.BuildRecord
import com.senzaproject.createapp.domain.builder.BuildState
import com.senzaproject.createapp.domain.builder.BuildStatus
import com.senzaproject.createapp.domain.builder.BuildType
import com.senzaproject.createapp.domain.builder.CheckLevel
import com.senzaproject.createapp.domain.builder.IssueSeverity
import com.senzaproject.createapp.domain.builder.WorkspaceLayout
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.terminal.TermuxStatus
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.ContentContainer
import com.senzaproject.createapp.ui.components.SectionCard
import com.senzaproject.createapp.ui.components.StatusChip
import com.senzaproject.createapp.ui.terminal.toText
import com.senzaproject.createapp.ui.theme.CodeTextStyle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private const val MAX_SHOWN_LOG_LINES = 400
private const val MAX_SHOWN_ISSUES = 50
private const val MAX_SHOWN_HISTORY = 15

@Composable
fun BuildScreen(
    viewModel: BuildViewModel,
    onOpenTerminal: () -> Unit,
    onOpenApkManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val build by viewModel.build.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Pengguna bisa memasang Termux atau mengubah izin di luar aplikasi, jadi periksa ulang setiap kembali.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshTermux() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshTermux()
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                // Izin permanen tidak bisa disimpan; folder tetap bisa dipakai selama sesi ini.
            }
            viewModel.onFolderPicked(uri.toString())
        }
    }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, context.getString(message.textRes()), Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    // Jam hanya berdetak selama build berjalan.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(build.isActive) {
        while (build.isActive) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val elapsedMillis = when {
        build.startedAtMillis == 0L -> 0L
        build.isActive -> now - build.startedAtMillis
        else -> build.finishedAtMillis - build.startedAtMillis
    }

    val currentProject = viewModel.currentProject()

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.nav_build),
            actions = {
                TextButton(onClick = onOpenApkManager) { Text(stringResource(R.string.apk_title)) }
            },
        )
        ContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ProjectCard(
                        projects = projects,
                        selected = currentProject,
                        enabled = !build.isActive,
                        onSelect = viewModel::selectProject,
                    )
                }
                item {
                    TypeCard(
                        selected = viewModel.selectedType,
                        enabled = !build.isActive,
                        onSelect = viewModel::selectType,
                    )
                }
                item {
                    EnvironmentCard(
                        termuxStatus = viewModel.termuxStatus,
                        sharedPath = viewModel.sharedPath,
                        toolchain = viewModel.toolchain,
                        buildActive = build.isActive,
                        onRequestPermission = { permissionLauncher.launch(TermuxPaths.PERMISSION_RUN_COMMAND) },
                        onOpenTerminal = onOpenTerminal,
                        onPickFolder = {
                            try {
                                folderLauncher.launch(null)
                            } catch (e: ActivityNotFoundException) {
                                viewModel.onPickerUnavailable()
                            }
                        },
                        onCheck = viewModel::checkToolchain,
                        onClean = viewModel::cleanTermuxWork,
                    )
                }
                item {
                    RunCard(
                        build = build,
                        elapsedMillis = elapsedMillis,
                        onStart = viewModel::startBuild,
                        onCancel = viewModel::cancelBuild,
                        onDismiss = viewModel::dismissResult,
                    )
                }
                if (build.artifacts.isNotEmpty()) {
                    item { ArtifactsCard(build = build, onOpenApkManager = onOpenApkManager) }
                }
                if (build.issues.isNotEmpty()) {
                    item { IssuesCard(build = build) }
                }
                if (build.logLines.isNotEmpty()) {
                    item { LogCard(build = build) }
                }
                item {
                    HistoryCard(
                        records = viewModel.historyRecords,
                        onOpen = viewModel::openLog,
                        onClear = viewModel::clearHistory,
                    )
                }
                item { CloudCard() }
            }
        }
    }

    val opened = viewModel.openedLog
    if (opened != null) {
        LogDialog(opened = opened, onClose = viewModel::closeLog)
    }
}

// --- Kartu-kartu ---

@Composable
private fun ProjectCard(
    projects: List<Project>,
    selected: Project?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    SectionCard(title = stringResource(R.string.bld_section_project)) {
        if (projects.isEmpty()) {
            Text(stringResource(R.string.bld_no_projects), style = MaterialTheme.typography.bodyMedium)
        } else {
            var open by rememberSaveable { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { open = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = selected?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                open = false
                                onSelect(project.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeCard(
    selected: BuildType,
    enabled: Boolean,
    onSelect: (BuildType) -> Unit,
) {
    SectionCard(title = stringResource(R.string.bld_section_type)) {
        BuildType.entries.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = type == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(type) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = type == selected, onClick = null, enabled = enabled)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(type.labelRes()), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (selected != BuildType.DEBUG) {
            Text(
                text = stringResource(R.string.bld_type_release_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EnvironmentCard(
    termuxStatus: TermuxStatus,
    sharedPath: String?,
    toolchain: ToolchainState,
    buildActive: Boolean,
    onRequestPermission: () -> Unit,
    onOpenTerminal: () -> Unit,
    onPickFolder: () -> Unit,
    onCheck: () -> Unit,
    onClean: () -> Unit,
) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.bld_section_env)) {
        when (termuxStatus) {
            TermuxStatus.NotInstalled -> {
                Text(stringResource(R.string.terminal_termux_not_installed), style = MaterialTheme.typography.bodyMedium)
            }

            TermuxStatus.PermissionMissing -> {
                Text(
                    stringResource(R.string.terminal_termux_permission_needed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.terminal_termux_grant))
                }
            }

            is TermuxStatus.Ready -> {
                Text(stringResource(R.string.terminal_termux_ready), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.bld_termux_setup_hint), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onOpenTerminal) {
            Text(stringResource(R.string.bld_open_terminal))
        }

        Text(
            text = stringResource(R.string.bld_folder_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = sharedPath ?: stringResource(R.string.bld_folder_none),
            style = CodeTextStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(stringResource(R.string.bld_folder_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onPickFolder, enabled = !buildActive) {
            Text(
                stringResource(if (sharedPath == null) R.string.bld_folder_pick else R.string.bld_folder_change),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCheck, enabled = toolchain !is ToolchainState.Checking) {
                Text(
                    stringResource(
                        if (toolchain is ToolchainState.Checking) R.string.bld_checking else R.string.bld_check,
                    ),
                )
            }
            OutlinedButton(onClick = onClean, enabled = !buildActive) {
                Text(stringResource(R.string.bld_clean))
            }
        }

        when (toolchain) {
            ToolchainState.Unknown -> Unit
            ToolchainState.Checking -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is ToolchainState.Failed -> {
                Text(
                    text = stringResource(R.string.bld_check_failed) + "\n" +
                        toolchain.result.failure.toText(context, toolchain.result.detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is ToolchainState.Ready -> {
                toolchain.report.items.forEach { item ->
                    ToolchainRow(
                        label = toolchainItemLabel(context, item.id),
                        level = item.level,
                        detail = item.detail,
                    )
                }
                Text(
                    text = stringResource(
                        if (toolchain.report.isReady) R.string.bld_check_ready else R.string.bld_check_incomplete,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ToolchainRow(label: String, level: CheckLevel, detail: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (level) {
            CheckLevel.OK -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )

            CheckLevel.WARN -> Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )

            CheckLevel.MISSING -> Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RunCard(
    build: BuildState,
    elapsedMillis: Long,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val chipColors = statusChipColors(build.status)

    SectionCard(title = stringResource(R.string.bld_section_run)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusChip(
                text = stringResource(build.status.labelRes()),
                containerColor = chipColors.first,
                contentColor = chipColors.second,
            )
            if (build.status != BuildStatus.IDLE) {
                Text(
                    stringResource(R.string.bld_elapsed, formatElapsed(elapsedMillis)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val phase = build.phase
        if (build.isActive && phase != null) {
            Text(stringResource(phase.labelRes()), style = MaterialTheme.typography.bodyMedium)
        }
        val task = build.currentTask
        if (task != null) {
            Text(
                text = stringResource(R.string.bld_current_task, task),
                style = CodeTextStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (build.tasksSeen > 0) {
            Text(
                stringResource(R.string.bld_tasks_count, build.tasksSeen),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val gradleDuration = build.gradleDuration
        if (gradleDuration != null) {
            Text(
                stringResource(R.string.bld_gradle_time, gradleDuration),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (build.isActive) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (build.isActive && build.liveLog) {
            Text(stringResource(R.string.bld_live_active), style = MaterialTheme.typography.bodySmall)
        } else if (build.isActive && build.noResponseYet) {
            Text(stringResource(R.string.bld_no_response), style = MaterialTheme.typography.bodySmall)
        }
        val failure = build.failure
        if (build.status == BuildStatus.FAILED && failure != null) {
            Text(
                text = failure.toText(context),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (build.mayStillRun) {
            Text(stringResource(R.string.bld_may_still_run), style = MaterialTheme.typography.bodySmall)
        }
        if (build.outputTruncated) {
            Text(stringResource(R.string.bld_output_truncated), style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !build.isActive) {
                Text(stringResource(R.string.bld_start))
            }
            if (build.isActive) {
                OutlinedButton(onClick = onCancel, enabled = build.status == BuildStatus.RUNNING) {
                    Text(
                        stringResource(
                            if (build.status == BuildStatus.CANCELLING) R.string.bld_cancelling else R.string.bld_cancel,
                        ),
                    )
                }
            } else if (build.status != BuildStatus.IDLE) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.bld_dismiss))
                }
            }
        }
    }
}

/** Pasangan (warna wadah, warna isi) untuk chip status build. */
@Composable
private fun statusChipColors(status: BuildStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        BuildStatus.SUCCESS -> Pair(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        BuildStatus.FAILED -> Pair(scheme.errorContainer, scheme.onErrorContainer)
        BuildStatus.RUNNING -> Pair(scheme.primaryContainer, scheme.onPrimaryContainer)
        BuildStatus.CANCELLING -> Pair(scheme.primaryContainer, scheme.onPrimaryContainer)
        BuildStatus.IDLE -> Pair(scheme.surfaceVariant, scheme.onSurfaceVariant)
        BuildStatus.CANCELLED -> Pair(scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

@Composable
private fun ArtifactsCard(build: BuildState, onOpenApkManager: () -> Unit) {
    val context = LocalContext.current
    val request = build.request
    val location = if (request == null) {
        null
    } else {
        WorkspaceLayout.outputPath(request.sharedRootPath, request.projectId)
    }
    SectionCard(title = stringResource(R.string.bld_section_result)) {
        build.artifacts.forEach { artifact -> ArtifactRow(artifact) }
        Button(onClick = onOpenApkManager) { Text(stringResource(R.string.bld_open_apk_manager)) }
        if (location != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.bld_artifact_location, location),
                    style = CodeTextStyle,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { copyToClipboard(context, location) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.bld_log_copy))
                }
            }
        }
    }
}

@Composable
private fun ArtifactRow(artifact: BuildArtifact) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(artifact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(
            text = artifact.kind.name + " • " + formatSize(artifact.sizeBytes) + " • " +
                stringResource(artifact.validation.labelRes()),
            style = MaterialTheme.typography.bodySmall,
        )
        if (artifact.isUnsigned) {
            Text(
                stringResource(R.string.bld_artifact_unsigned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun IssuesCard(build: BuildState) {
    SectionCard(title = stringResource(R.string.bld_section_issues, build.issues.size)) {
        build.issues.take(MAX_SHOWN_ISSUES).forEach { issue -> IssueRow(issue) }
        if (build.issues.size > MAX_SHOWN_ISSUES || build.issuesTruncated) {
            Text(stringResource(R.string.bld_issues_truncated), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IssueRow(issue: BuildIssue) {
    val isError = issue.severity == IssueSeverity.ERROR
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(if (isError) R.string.bld_issue_error else R.string.bld_issue_warning),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        )
        Text(issue.message, style = MaterialTheme.typography.bodyMedium)
        val file = issue.file
        if (file != null) {
            val line = issue.line
            val where = if (line != null) "$file:$line" else file
            Text(where, style = CodeTextStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LogCard(build: BuildState) {
    val context = LocalContext.current
    val shown = build.logLines.takeLast(MAX_SHOWN_LOG_LINES).joinToString("\n")
    SectionCard(title = stringResource(R.string.bld_section_log)) {
        if (build.droppedLogLines > 0) {
            Text(
                stringResource(R.string.bld_log_dropped, build.droppedLogLines),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(text = shown, style = CodeTextStyle)
            }
        }
        TextButton(
            onClick = {
                copyToClipboard(context, build.logLines.joinToString("\n"))
                Toast.makeText(context, R.string.bld_log_copied, Toast.LENGTH_SHORT).show()
            },
        ) {
            Text(stringResource(R.string.bld_log_copy))
        }
    }
}

@Composable
private fun HistoryCard(
    records: List<BuildRecord>,
    onOpen: (BuildRecord) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.bld_section_history)) {
        if (records.isEmpty()) {
            Text(stringResource(R.string.bld_history_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            records.take(MAX_SHOWN_HISTORY).forEach { record ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(record) }
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = record.projectName + " • " + stringResource(record.type.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val stamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(record.startedAtMillis))
                    Text(
                        text = stringResource(record.status.labelRes()) + " • " + stamp + " • " +
                            formatElapsed(record.durationMillis) +
                            if (record.errorCount > 0) " • " + stringResource(R.string.bld_history_item_errors, record.errorCount) else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val reason = failureCodeText(context, record.failureCode, record.failureDetail)
                    if (reason != null) {
                        Text(
                            reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            TextButton(onClick = onClear) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.bld_history_clear))
            }
        }
    }
}

@Composable
private fun CloudCard() {
    SectionCard(title = stringResource(R.string.bld_section_cloud)) {
        Text(stringResource(R.string.bld_cloud_desc), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LogDialog(opened: OpenedLog, onClose: () -> Unit) {
    val context = LocalContext.current
    val text = opened.text
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.bld_log_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = text ?: stringResource(R.string.bld_log_missing),
                    style = CodeTextStyle,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.bld_close)) }
        },
        dismissButton = {
            if (text != null) {
                TextButton(
                    onClick = {
                        copyToClipboard(context, text)
                        Toast.makeText(context, R.string.bld_log_copied, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(stringResource(R.string.bld_log_copy))
                }
            }
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Create App", text))
}
