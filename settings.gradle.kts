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
// Update-Erkennung). Liegt im Monorepo neben apps/ und wird hier als Modul
// eingebunden.
include(":common")
project(":common").projectDir = file("../../common/android")
