package com.senzaproject.createapp.navigation

/** Rute navigasi. Rute string dipilih agar tidak perlu plugin serialization pada Tahap 1. */
object Routes {
    const val HOME = "home"
    const val PROJECTS = "projects"
    const val BUILD = "build"
    const val GITHUB = "github"
    const val SETTINGS = "settings"

    // Layar sekunder (dibuka dari layar lain, tidak ada di bottom navigation)
    const val EDITOR = "editor"
    const val TERMINAL = "terminal"
}
