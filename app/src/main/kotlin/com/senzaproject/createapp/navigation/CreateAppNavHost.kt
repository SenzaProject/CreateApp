package com.senzaproject.createapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.senzaproject.createapp.R
import com.senzaproject.createapp.ui.apk.ApkManagerScreen
import com.senzaproject.createapp.ui.apk.ApkManagerViewModel
import com.senzaproject.createapp.ui.builder.BuildScreen
import com.senzaproject.createapp.ui.builder.BuildViewModel
import com.senzaproject.createapp.ui.components.PlaceholderScreen
import com.senzaproject.createapp.ui.editor.EditorScreen
import com.senzaproject.createapp.ui.editor.EditorViewModel
import com.senzaproject.createapp.ui.explorer.ExplorerScreen
import com.senzaproject.createapp.ui.explorer.ExplorerViewModel
import com.senzaproject.createapp.ui.home.HomeNavigation
import com.senzaproject.createapp.ui.home.HomeScreen
import com.senzaproject.createapp.ui.home.HomeViewModel
import com.senzaproject.createapp.ui.projects.ProjectsScreen
import com.senzaproject.createapp.ui.projects.ProjectsViewModel
import com.senzaproject.createapp.ui.settings.SettingsScreen
import com.senzaproject.createapp.ui.settings.SettingsViewModel
import com.senzaproject.createapp.ui.splash.SplashScreen
import com.senzaproject.createapp.ui.terminal.TerminalScreen
import com.senzaproject.createapp.ui.terminal.TerminalViewModel

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
                    onOpenProject = { id -> navController.navigateSecondary(Routes.explorer(id)) },
                ),
            )
        }

        composable(Routes.PROJECTS) {
            val viewModel: ProjectsViewModel = viewModel(factory = viewModelFactory)
            ProjectsScreen(
                viewModel = viewModel,
                onOpenProject = { id -> navController.navigateSecondary(Routes.explorer(id)) },
            )
        }

        composable(
            route = Routes.EXPLORER,
            arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.StringType }),
        ) { entry ->
            val projectId = entry.arguments?.getString(Routes.ARG_PROJECT_ID).orEmpty()
            val explorerViewModel: ExplorerViewModel = viewModel(factory = viewModelFactory)
            // ViewModel Editor berskala Activity: file dibuka lewat ViewModel ini lalu layar Editor ditampilkan.
            val editorViewModel: EditorViewModel = viewModel(
                viewModelStoreOwner = activityViewModelStoreOwner,
                factory = viewModelFactory,
            )
            ExplorerScreen(
                projectId = projectId,
                viewModel = explorerViewModel,
                onBack = { navController.popBackStack() },
                onOpenFile = { path ->
                    editorViewModel.openProjectFile(projectId, path)
                    navController.navigateSecondary(Routes.EDITOR)
                },
            )
        }

        composable(Routes.BUILD) {
            // ViewModel berskala Activity agar pilihan dan hasil build tetap ada saat pindah tab.
            val viewModel: BuildViewModel = viewModel(
                viewModelStoreOwner = activityViewModelStoreOwner,
                factory = viewModelFactory,
            )
            BuildScreen(
                viewModel = viewModel,
                onOpenTerminal = { navController.navigateSecondary(Routes.TERMINAL) },
                onOpenApkManager = { navController.navigateSecondary(Routes.APKS) },
            )
        }

        composable(Routes.APKS) {
            val viewModel: ApkManagerViewModel = viewModel(factory = viewModelFactory)
            ApkManagerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
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
            // ViewModel berskala Activity: scrollback, masukan, dan perintah yang berjalan bertahan saat pindah layar.
            val viewModel: TerminalViewModel = viewModel(
                viewModelStoreOwner = activityViewModelStoreOwner,
                factory = viewModelFactory,
            )
            TerminalScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
