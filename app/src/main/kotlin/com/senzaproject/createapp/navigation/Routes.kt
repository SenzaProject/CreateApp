package com.senzaproject.createapp.navigation

/** Rute navigasi. Rute string dipilih agar tidak perlu plugin serialization. */
object Routes {
    const val SPLASH = "splash"

    // Tab bottom navigation
    const val HOME = "home"
    const val PROJECTS = "projects"
    const val BUILD = "build"
    const val GITHUB = "github"
    const val SETTINGS = "settings"

    // Layar sekunder (dibuka dari layar lain, layar penuh tanpa bottom navigation)
    const val EDITOR = "editor"
    const val TERMINAL = "terminal"
}
