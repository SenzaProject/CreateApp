package com.senzaproject.createapp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.senzaproject.createapp.navigation.CreateAppNavHost
import com.senzaproject.createapp.navigation.TopLevelDestination
import com.senzaproject.createapp.navigation.navigateTopLevel

@Composable
fun CreateAppRoot(viewModelFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Pada setContent milik Activity, ini adalah ViewModelStoreOwner level Activity.
    val activityViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "ViewModelStoreOwner tidak tersedia"
    }

    // Bottom navigation hanya tampil di tab utama (tidak di Splash, Editor, Terminal).
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Setiap layar mengurus inset status bar lewat top bar-nya; bottom bar mengurus inset bawah.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                CreateAppBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { destination -> navController.navigateTopLevel(destination.route) },
                )
            }
        },
    ) { innerPadding ->
        CreateAppNavHost(
            navController = navController,
            viewModelFactory = viewModelFactory,
            activityViewModelStoreOwner = activityViewModelStoreOwner,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun CreateAppBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = scheme.surface) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(text = stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = scheme.primary,
                    selectedTextColor = scheme.primary,
                    indicatorColor = scheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                ),
            )
        }
    }
}
