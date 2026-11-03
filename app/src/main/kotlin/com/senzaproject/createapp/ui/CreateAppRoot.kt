package com.senzaproject.createapp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.senzaproject.createapp.navigation.CreateAppNavHost
import com.senzaproject.createapp.navigation.TopLevelDestination

@Composable
fun CreateAppRoot(viewModelFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Setiap layar mengurus inset status bar lewat top bar-nya; bottom bar mengurus inset bawah.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            CreateAppBottomBar(
                currentDestination = backStackEntry?.destination,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        CreateAppNavHost(
            navController = navController,
            viewModelFactory = viewModelFactory,
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
