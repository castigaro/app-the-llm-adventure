pluginManagement {
    repositories {
        google()
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

rootProject.name = "TheLlmAdventure"
include(":app")

// Gemeinsame Bibliothek der AppSonar-Apps (Basis-Theme, KI-Einstellungen,
// Update-Erkennung). Liegt als Geschwister-Repo neben diesem Repository und wird hier als Modul
// eingebunden.
include(":common")
project(":common").projectDir = file("../android-apps-common/android")
