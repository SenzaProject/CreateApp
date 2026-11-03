package com.senzaproject.createapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.senzaproject.createapp.R
import com.senzaproject.createapp.ui.components.PlaceholderScreen
import com.senzaproject.createapp.ui.home.HomeScreen
import com.senzaproject.createapp.ui.home.HomeViewModel
import com.senzaproject.createapp.ui.settings.SettingsScreen

@Composable
fun CreateAppNavHost(
    navController: NavHostController,
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            HomeScreen(
                viewModel = viewModel,
                onOpenEditor = { navController.navigate(Routes.EDITOR) },
                onOpenTerminal = { navController.navigate(Routes.TERMINAL) },
            )
        }

        composable(Routes.PROJECTS) {
            PlaceholderScreen(
                title = stringResource(R.string.nav_projects),
                description = stringResource(R.string.placeholder_projects_desc),
                plannedStage = 3,
                icon = Icons.Filled.Folder,
            )
        }

        composable(Routes.BUILD) {
            PlaceholderScreen(
                title = stringResource(R.string.nav_build),
                description = stringResource(R.string.placeholder_build_desc),
                plannedStage = 6,
                icon = Icons.Filled.Build,
            )
        }

        composable(Routes.GITHUB) {
            PlaceholderScreen(
                title = stringResource(R.string.nav_github),
                description = stringResource(R.string.placeholder_github_desc),
                plannedStage = 9,
                icon = Icons.Filled.Cloud,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }

        composable(Routes.EDITOR) {
            PlaceholderScreen(
                title = stringResource(R.string.title_editor),
                description = stringResource(R.string.placeholder_editor_desc),
                plannedStage = 4,
                icon = Icons.Filled.Code,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TERMINAL) {
            PlaceholderScreen(
                title = stringResource(R.string.title_terminal),
                description = stringResource(R.string.placeholder_terminal_desc),
                plannedStage = 5,
                icon = Icons.Filled.PlayArrow,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
