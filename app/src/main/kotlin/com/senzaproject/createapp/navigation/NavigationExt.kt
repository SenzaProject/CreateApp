package com.senzaproject.createapp.navigation

import androidx.navigation.NavHostController

/**
 * Pindah ke tab utama dengan perilaku bottom navigation standar: tidak menumpuk back stack,
 * satu salinan per tab, dan status tab dipulihkan. Dasar back stack adalah HOME karena
 * SPLASH sudah dihapus dari back stack setelah selesai.
 */
fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Membuka layar sekunder (Editor, Terminal) tanpa menggandakannya di back stack. */
fun NavHostController.navigateSecondary(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}
