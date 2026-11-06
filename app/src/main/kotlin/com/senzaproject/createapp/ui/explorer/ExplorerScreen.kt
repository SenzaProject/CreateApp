package com.senzaproject.createapp.ui.explorer

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.project.FileNode
import com.senzaproject.createapp.domain.project.NewFileType
import com.senzaproject.createapp.domain.project.checkEntryName
import com.senzaproject.createapp.domain.project.fileNameFor
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.ui.common.messageRes
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.ConfirmDestructiveDialog
import com.senzaproject.createapp.ui.components.ContentContainer
import com.senzaproject.createapp.ui.components.EmptyState
import com.senzaproject.createapp.ui.components.NameDialog

private sealed interface ExplorerDialog {
    data object NewFile : ExplorerDialog
    data object NewFolder : ExplorerDialog
    data class Rename(val node: FileNode) : ExplorerDialog
    data class Delete(val node: FileNode) : ExplorerDialog
}

/**
 * File Explorer (Tahap 3): jelajah folder proyek, buat file (Kotlin/XML/JSON/Gradle/lainnya) dan
 * folder, ganti nama, dan hapus dengan konfirmasi. Mengetuk file memanggil [onOpenFile].
 */
@Composable
fun ExplorerScreen(
    projectId: String,
    viewModel: ExplorerViewModel,
    onBack: () -> Unit,
    onOpenFile: (path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy = viewModel.isBusy
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }
    var dialogError by remember { mutableStateOf<AppError?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.currentDir.isNotEmpty()) { viewModel.navigateUp() }

    val message = viewModel.message
    val messageText = message?.let { explorerMessageText(it) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    fun openDialog(value: ExplorerDialog) {
        dialogError = null
        dialog = value
    }

    // Layar penuh tanpa bottom navigation: sisakan ruang untuk navigation bar sistem (edge-to-edge).
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = state.projectName.ifEmpty { stringResource(R.string.nav_projects) },
                onBack = { if (!viewModel.navigateUp()) onBack() },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.explorer_refresh),
                        )
                    }
                },
            )
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            ContentContainer {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "/" + state.currentDir,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { openDialog(ExplorerDialog.NewFile) },
                            modifier = Modifier.weight(1f),
                            enabled = !busy && state.error == null,
                        ) {
                            Icon(imageVector = Icons.Filled.NoteAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.explorer_new_file))
                        }
                        OutlinedButton(
                            onClick = { openDialog(ExplorerDialog.NewFolder) },
                            modifier = Modifier.weight(1f),
                            enabled = !busy && state.error == null,
                        ) {
                            Icon(imageVector = Icons.Filled.CreateNewFolder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.explorer_new_folder))
                        }
                    }
                    ExplorerBody(
                        entries = state.entries,
                        error = state.error,
                        busy = busy,
                        onRetry = viewModel::refresh,
                        onOpenDirectory = viewModel::openDirectory,
                        onOpenFile = onOpenFile,
                        onRename = { openDialog(ExplorerDialog.Rename(it)) },
                        onDelete = { openDialog(ExplorerDialog.Delete(it)) },
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    when (val current = dialog) {
        ExplorerDialog.NewFile -> NewFileDialog(
            busy = busy,
            serverError = dialogError,
            onDismiss = { dialog = null },
            onCreate = { baseName, type ->
                dialogError = null
                viewModel.createFile(baseName, type) { error ->
                    if (error == null) dialog = null else dialogError = error
                }
            },
        )

        ExplorerDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.explorer_new_folder_title),
            label = stringResource(R.string.explorer_name_label),
            initialValue = "",
            confirmLabel = stringResource(R.string.action_create),
            validate = { checkEntryName(it) },
            busy = busy,
            serverError = dialogError,
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialogError = null
                viewModel.createFolder(name) { error ->
                    if (error == null) dialog = null else dialogError = error
                }
            },
        )

        is ExplorerDialog.Rename -> {
            val target = current.node
            NameDialog(
                title = stringResource(R.string.explorer_rename_title),
                label = stringResource(R.string.explorer_name_label),
                initialValue = target.name,
                confirmLabel = stringResource(R.string.action_rename),
                validate = { checkEntryName(it) },
                busy = busy,
                serverError = dialogError,
                onDismiss = { dialog = null },
                onConfirm = { newName ->
                    dialogError = null
                    viewModel.rename(target, newName) { error ->
                        if (error == null) dialog = null else dialogError = error
                    }
                },
            )
        }

        is ExplorerDialog.Delete -> {
            val target = current.node
            ConfirmDestructiveDialog(
                title = stringResource(
                    if (target.isDirectory) R.string.explorer_delete_folder_title else R.string.explorer_delete_file_title,
                ),
                message = stringResource(
                    if (target.isDirectory) R.string.explorer_delete_folder_message else R.string.explorer_delete_file_message,
                    target.name,
                ),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    dialog = null
                    viewModel.delete(target)
                },
                onDismiss = { dialog = null },
            )
        }

        null -> Unit
    }
}

@Composable
private fun ExplorerBody(
    entries: List<FileNode>?,
    error: AppError?,
    busy: Boolean,
    onRetry: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit,
) {
    when {
        error != null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }

        entries == null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        entries.isEmpty() -> EmptyState(
            icon = Icons.Filled.Folder,
            title = stringResource(R.string.explorer_empty_title),
            description = stringResource(R.string.explorer_empty_desc),
        )

        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = entries, key = { it.path }) { node ->
                ExplorerRow(
                    node = node,
                    enabled = !busy,
                    onClick = { if (node.isDirectory) onOpenDirectory(node.path) else onOpenFile(node.path) },
                    onRename = { onRename(node) },
                    onDelete = { onDelete(node) },
                )
            }
        }
    }
}

@Composable
private fun ExplorerRow(
    node: FileNode,
    enabled: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val subtitle = remember(node.sizeBytes, node.isDirectory) {
        if (node.isDirectory) "" else Formatter.formatShortFileSize(context, node.sizeBytes)
    }
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (node.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { expanded = true }, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.explorer_item_menu),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** Dialog file baru: nama dasar + jenis file (menentukan ekstensi dan isi awal). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewFileDialog(
    busy: Boolean,
    serverError: AppError?,
    onDismiss: () -> Unit,
    onCreate: (baseName: String, type: NewFileType?) -> Unit,
) {
    var type by remember { mutableStateOf<NewFileType?>(NewFileType.KOTLIN) }

    NameDialog(
        title = stringResource(R.string.explorer_new_file_title),
        label = stringResource(R.string.explorer_name_label),
        initialValue = "",
        confirmLabel = stringResource(R.string.action_create),
        validate = { input ->
            val finalName = type?.let { fileNameFor(input, it) } ?: input
            checkEntryName(finalName)
        },
        busy = busy,
        serverError = serverError,
        onDismiss = onDismiss,
        onConfirm = { input -> onCreate(input, type) },
        extraContent = {
            Text(
                text = stringResource(R.string.explorer_file_type_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FileTypeChip(label = stringResource(R.string.file_type_kotlin), selected = type == NewFileType.KOTLIN) {
                    type = NewFileType.KOTLIN
                }
                FileTypeChip(label = stringResource(R.string.file_type_xml), selected = type == NewFileType.XML) {
                    type = NewFileType.XML
                }
                FileTypeChip(label = stringResource(R.string.file_type_json), selected = type == NewFileType.JSON) {
                    type = NewFileType.JSON
                }
                FileTypeChip(label = stringResource(R.string.file_type_gradle), selected = type == NewFileType.GRADLE) {
                    type = NewFileType.GRADLE
                }
                FileTypeChip(label = stringResource(R.string.file_type_other), selected = type == null) {
                    type = null
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileTypeChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text(label) },
    )
}

@Composable
private fun explorerMessageText(message: ExplorerMessage): String = when (message) {
    is ExplorerMessage.Created -> stringResource(R.string.explorer_msg_created, message.name)
    is ExplorerMessage.Renamed -> stringResource(R.string.explorer_msg_renamed, message.name)
    is ExplorerMessage.Deleted -> stringResource(R.string.explorer_msg_deleted, message.name)
    is ExplorerMessage.Failed -> stringResource(message.error.messageRes())
}
