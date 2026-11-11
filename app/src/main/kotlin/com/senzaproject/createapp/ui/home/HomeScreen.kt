package com.senzaproject.createapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.ui.components.ActionTileData
import com.senzaproject.createapp.ui.components.ActionTileGrid
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.ContentContainer
import com.senzaproject.createapp.ui.components.EmptyState
import com.senzaproject.createapp.ui.components.ProjectListItem
import com.senzaproject.createapp.ui.theme.CodeTextStyle

/** Aksi navigasi dari Dashboard; diisi oleh NavHost agar layar tidak mengenal rute. */
class HomeNavigation(
    val onProjects: () -> Unit,
    val onEditor: () -> Unit,
    val onBuild: () -> Unit,
    val onTerminal: () -> Unit,
    val onGitHub: () -> Unit,
    val onOpenProject: (projectId: String) -> Unit = {},
)

private val TileBlue = Color(0xFF2563EB)
private val TileGreen = Color(0xFF16A34A)
private val TilePurple = Color(0xFF7C3AED)
private val TileOrange = Color(0xFFEA580C)
private val TileTeal = Color(0xFF0891B2)
private val TileSlate = Color(0xFF475569)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    val recentProjects by viewModel.recentProjects.collectAsStateWithLifecycle()
    val lastCrash by viewModel.lastCrash.collectAsStateWithLifecycle()

    val terminalIcon: ImageVector = ImageVector.vectorResource(R.drawable.ic_terminal)
    // Subjudul tile jujur: fitur yang belum ada menampilkan tahap jadwalnya, bukan janji palsu.
    val tiles = listOf(
        ActionTileData(
            icon = Icons.Filled.Add,
            title = stringResource(R.string.tile_new_project),
            subtitle = stringResource(R.string.tile_new_project_sub),
            color = TileBlue,
            onClick = navigation.onProjects,
        ),
        ActionTileData(
            icon = Icons.Filled.FolderOpen,
            title = stringResource(R.string.tile_open_project),
            subtitle = stringResource(R.string.tile_open_project_sub),
            color = TileGreen,
            onClick = navigation.onProjects,
        ),
        ActionTileData(
            icon = Icons.Filled.Code,
            title = stringResource(R.string.tile_editor),
            subtitle = stringResource(R.string.tile_editor_basic),
            color = TileTeal,
            onClick = navigation.onEditor,
        ),
        ActionTileData(
            icon = Icons.Filled.Build,
            title = stringResource(R.string.tile_build_apk),
            subtitle = stringResource(R.string.tile_build_apk_sub),
            color = TilePurple,
            onClick = navigation.onBuild,
        ),
        ActionTileData(
            icon = terminalIcon,
            title = stringResource(R.string.tile_terminal),
            subtitle = stringResource(R.string.tile_terminal_sub),
            color = TileOrange,
            onClick = navigation.onTerminal,
        ),
        ActionTileData(
            icon = Icons.Filled.Cloud,
            title = stringResource(R.string.tile_github),
            subtitle = stringResource(R.string.tile_stage, 9),
            color = TileSlate,
            onClick = navigation.onGitHub,
        ),
    )

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.app_name))
        ContentContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WelcomeCard()
                ActionTileGrid(tiles = tiles)
                RecentProjectsSection(
                    projects = recentProjects,
                    onSeeAll = navigation.onProjects,
                    onOpenProject = navigation.onOpenProject,
                )
                lastCrash?.let { crash ->
                    CrashCard(report = crash, onClear = viewModel::clearCrashReport)
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_welcome_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.home_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RecentProjectsSection(
    projects: List<Project>?,
    onSeeAll: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_recent_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) {
                Text(text = stringResource(R.string.home_see_all))
            }
        }
        when {
            projects == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            projects.isEmpty() -> EmptyState(
                icon = Icons.Filled.Folder,
                title = stringResource(R.string.home_recent_empty_title),
                description = stringResource(R.string.home_recent_empty_desc),
            )

            else -> projects.forEach { project ->
                ProjectListItem(project = project, onClick = { onOpenProject(project.id) })
            }
        }
    }
}

@Composable
private fun CrashCard(report: String, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.crash_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.crash_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = report,
                style = CodeTextStyle,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
            Button(onClick = onClear) {
                Text(text = stringResource(R.string.crash_clear))
            }
        }
    }
}
