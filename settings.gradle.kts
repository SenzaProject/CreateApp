pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CreateApp"

// Presentation
include(":app")

// Clean Architecture
include(":domain")
include(":data")

// Modul fitur / infrastruktur
// Catatan: folder ":core:builder" sengaja tidak bernama "build" agar tidak bentrok
// dengan folder output Gradle (build/) dan aturan .gitignore.
include(":core:common")
include(":core:editor")
include(":core:project")
include(":core:builder")
include(":core:terminal")
include(":core:git")
