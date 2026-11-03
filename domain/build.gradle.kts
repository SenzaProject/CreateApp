import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Lapisan domain: Kotlin murni (tanpa Android) agar mudah diuji dan tidak bergantung pada framework.
plugins {
    // java-library menyediakan konfigurasi `api` yang dipakai di bawah.
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
