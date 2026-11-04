package com.senzaproject.createapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.senzaproject.createapp.R
import com.senzaproject.createapp.ui.components.PlaceholderScreen
import com.senzaproject.createapp.ui.editor.EditorScreen
import com.senzaproject.createapp.ui.editor.EditorViewModel
import com.senzaproject.createapp.ui.home.HomeNavigation
import com.senzaproject.createapp.ui.home.HomeScreen
import com.senzaproject.createapp.ui.home.HomeViewModel
import com.senzaproject.createapp.ui.projects.ProjectsScreen
import com.senzaproject.createapp.ui.projects.ProjectsViewModel
import com.senzaproject.createapp.ui.settings.SettingsScreen
import com.senzaproject.createapp.ui.settings.SettingsViewModel
import com.senzaproject.createapp.ui.splash.SplashScreen

/**
 * @param activityViewModelStoreOwner pemilik ViewModel level Activity; dipakai agar buffer editor
 *   tidak hilang saat layar Editor ditutup lalu dibuka lagi.
 */
@Composable
fun CreateAppNavHost(
    navController: NavHostController,
    viewModelFactory: ViewModelProvider.Factory,
    activityViewModelStoreOwner: ViewModelStoreOwner,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            HomeScreen(
                viewModel = viewModel,
                navigation = HomeNavigation(
                    onProjects = { navController.navigateTopLevel(Routes.PROJECTS) },
                    onEditor = { navController.navigateSecondary(Routes.EDITOR) },
                    onBuild = { navController.navigateTopLevel(Routes.BUILD) },
                    onTerminal = { navController.navigateSecondary(Routes.TERMINAL) },
                    onGitHub = { navController.navigateTopLevel(Routes.GITHUB) },
                ),
            )
        }

        composable(Routes.PROJECTS) {
            val viewModel: ProjectsViewModel = viewModel(factory = viewModelFactory)
            ProjectsScreen(viewModel = viewModel)
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
            val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            SettingsScreen(viewModel = viewModel)
        }

        composable(Routes.EDITOR) {
            val viewModel: EditorViewModel = viewModel(
                viewModelStoreOwner = activityViewModelStoreOwner,
                factory = viewModelFactory,
            )
            EditorScreen(
                viewModel = viewModel,
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
