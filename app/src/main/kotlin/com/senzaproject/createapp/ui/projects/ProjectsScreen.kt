package com.senzaproject.createapp.ui.projects

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectFilter
import com.senzaproject.createapp.domain.project.checkProjectName
import com.senzaproject.createapp.domain.project.filterProjects
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.OperationFailure
import com.senzaproject.createapp.ui.common.messageRes
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.ConfirmDestructiveDialog
import com.senzaproject.createapp.ui.components.ContentContainer
import com.senzaproject.createapp.ui.components.EmptyState
import com.senzaproject.createapp.ui.components.NameDialog
import com.senzaproject.createapp.ui.components.ProjectListItem

private sealed interface ProjectDialog {
    data object Create : ProjectDialog
    data class Rename(val project: Project) : ProjectDialog
    data class Delete(val project: Project) : ProjectDialog
}

/**
 * Project Manager (Tahap 3): daftar, cari, filter, buat, buka folder (SAF), ganti nama, favorit,
 * ekspor, dan hapus dengan konfirmasi. Mengetuk proyek membuka File Explorer lewat [onOpenProject].
 */
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val query = viewModel.query
    val filter = viewModel.filter
    val busy = viewModel.isBusy
    val visibleProjects = remember(projects, query, filter) {
        projects?.let { filterProjects(it, query, filter) }
    }

    var dialog by remember { mutableStateOf<ProjectDialog?>(null) }
    var dialogError by remember { mutableStateOf<AppError?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val message = viewModel.message
    val messageText = message?.let { projectsMessageText(it) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.importProject(uri.toString())
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        viewModel.finishExport(uri?.toString())
    }

    fun launchImport() {
        try {
            importLauncher.launch(null)
        } catch (e: ActivityNotFoundException) {
            viewModel.onPickerUnavailable()
        }
    }

    fun launchExport(project: Project) {
        viewModel.beginExport(project)
        try {
            exportLauncher.launch(null)
        } catch (e: ActivityNotFoundException) {
            viewModel.finishExport(null)
            viewModel.onPickerUnavailable()
        }
    }

    fun openDialog(value: ProjectDialog) {
        dialogError = null
        dialog = value
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = stringResource(R.string.nav_projects))
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            ContentContainer {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { openDialog(ProjectDialog.Create) },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.project_action_new))
                        }
                        OutlinedButton(
                            onClick = { launchImport() },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        ) {
                            Icon(imageVector = Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.project_action_open))
                        }
                    }
                    SearchField(
                        query = query,
                        onQueryChange = viewModel::onQueryChange,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                    FilterRow(
                        selected = filter,
                        onSelect = viewModel::onFilterChange,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    when {
                        visibleProjects == null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        visibleProjects.isEmpty() -> {
                            val noProjectsAtAll = projects.isNullOrEmpty()
                            EmptyState(
                                icon = Icons.Filled.Folder,
                                title = stringResource(
                                    if (noProjectsAtAll) R.string.projects_empty_title else R.string.projects_no_results,
                                ),
                                description = if (noProjectsAtAll) stringResource(R.string.projects_empty_desc) else "",
                            )
                        }

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items = visibleProjects, key = { it.id }) { project ->
                                ProjectListItem(
                                    project = project,
                                    onClick = { onOpenProject(project.id) },
                                    trailing = {
                                        ProjectMenu(
                                            project = project,
                                            enabled = !busy,
                                            onToggleFavorite = { viewModel.toggleFavorite(project) },
                                            onRename = { openDialog(ProjectDialog.Rename(project)) },
                                            onExport = { launchExport(project) },
                                            onDelete = { openDialog(ProjectDialog.Delete(project)) },
                                        )
                                    },
                                )
                            }
                        }
                    }
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
        ProjectDialog.Create -> {
            val existingNames = remember(projects) { projects.orEmpty().map { it.name.lowercase() }.toSet() }
            CreateProjectDialog(
                existingNames = existingNames,
                busy = busy,
                serverError = dialogError,
                onDismiss = { dialog = null },
                onCreate = { request ->
                    dialogError = null
                    viewModel.createProject(request) { error ->
                        if (error == null) dialog = null else dialogError = error
                    }
                },
            )
        }

        is ProjectDialog.Rename -> {
            val target = current.project
            val otherNames = remember(projects, target.id) {
                projects.orEmpty().filter { it.id != target.id }.map { it.name.lowercase() }.toSet()
            }
            NameDialog(
                title = stringResource(R.string.project_rename_title),
                label = stringResource(R.string.project_name_label),
                initialValue = target.name,
                confirmLabel = stringResource(R.string.action_rename),
                validate = { input ->
                    checkProjectName(input)
                        ?: if (input.lowercase() in otherNames) OperationFailure.NAME_TAKEN else null
                },
                busy = busy,
                serverError = dialogError,
                onDismiss = { dialog = null },
                onConfirm = { newName ->
                    dialogError = null
                    viewModel.renameProject(target, newName) { error ->
                        if (error == null) dialog = null else dialogError = error
                    }
                },
            )
        }

        is ProjectDialog.Delete -> {
            val target = current.project
            ConfirmDestructiveDialog(
                title = stringResource(R.string.project_delete_title),
                message = stringResource(R.string.project_delete_message, target.name),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    dialog = null
                    viewModel.deleteProject(target)
                },
                onDismiss = { dialog = null },
            )
        }

        null -> Unit
    }
}

@Composable
private fun projectsMessageText(message: ProjectsMessage): String = when (message) {
    is ProjectsMessage.Created -> stringResource(R.string.project_msg_created, message.name)
    is ProjectsMessage.Renamed -> stringResource(R.string.project_msg_renamed, message.name)
    is ProjectsMessage.Deleted -> stringResource(R.string.project_msg_deleted, message.name)
    is ProjectsMessage.Imported -> stringResource(R.string.project_msg_imported, message.name)
    is ProjectsMessage.Exported -> stringResource(R.string.project_msg_exported, message.fileCount)
    is ProjectsMessage.Failed -> stringResource(message.error.messageRes())
}

@Composable
private fun ProjectMenu(
    project: Project,
    enabled: Boolean,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.project_menu),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (project.isFavorite) R.string.project_unfavorite else R.string.project_favorite_action,
                        ),
                    )
                },
                onClick = {
                    expanded = false
                    onToggleFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.project_export)) },
                onClick = {
                    expanded = false
                    onExport()
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

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text(text = stringResource(R.string.projects_search_hint)) },
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.projects_search_clear),
                    )
                }
            }
        } else {
            null
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    selected: ProjectFilter,
    onSelect: (ProjectFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProjectFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(text = stringResource(filter.labelRes())) },
            )
        }
    }
}

@StringRes
private fun ProjectFilter.labelRes(): Int = when (this) {
    ProjectFilter.ALL -> R.string.projects_filter_all
    ProjectFilter.RECENT -> R.string.projects_filter_recent
    ProjectFilter.FAVORITES -> R.string.projects_filter_favorites
}
