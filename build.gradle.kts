// Konfigurasi tingkat root. Plugin dideklarasikan di sini (apply false) agar versinya
// konsisten di semua modul dan classloader plugin Kotlin hanya dimuat sekali.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
