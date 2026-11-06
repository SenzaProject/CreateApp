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

    /** File Explorer satu proyek. Id proyek hanya berisi huruf, angka, `_`, dan `-` sehingga aman di rute. */
    const val EXPLORER = "explorer/{projectId}"
    const val ARG_PROJECT_ID = "projectId"

    fun explorer(projectId: String): String = "explorer/$projectId"
}
