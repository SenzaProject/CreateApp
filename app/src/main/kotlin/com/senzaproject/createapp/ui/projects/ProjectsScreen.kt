package com.senzaproject.createapp.ui.projects

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.project.ProjectFilter
import com.senzaproject.createapp.domain.project.filterProjects
import com.senzaproject.createapp.ui.components.AppTopBar
import com.senzaproject.createapp.ui.components.ContentContainer
import com.senzaproject.createapp.ui.components.EmptyState
import com.senzaproject.createapp.ui.components.ProjectListItem

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    modifier: Modifier = Modifier,
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val query = viewModel.query
    val filter = viewModel.filter
    val visibleProjects = remember(projects, query, filter) {
        projects?.let { filterProjects(it, query, filter) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.nav_projects))
        ContentContainer {
            Column(modifier = Modifier.fillMaxSize()) {
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
                            ProjectListItem(project = project)
                        }
                    }
                }
            }
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
