package com.senzaproject.createapp.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.senzaproject.createapp.R

/** Tujuan utama pada bottom navigation, mengikuti desain UI awal. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    PROJECTS(Routes.PROJECTS, R.string.nav_projects, Icons.Filled.Folder),
    BUILD(Routes.BUILD, R.string.nav_build, Icons.Filled.Build),
    GITHUB(Routes.GITHUB, R.string.nav_github, Icons.Filled.Cloud),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
}
