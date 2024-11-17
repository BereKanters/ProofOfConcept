pluginManagement {
    repositories {
        google()  // Main Google repository for Android-related plugins
        mavenCentral()  // Central Maven repository
        gradlePluginPortal()  // Gradle Plugin Portal for non-Android plugins

        // Optional: restrict content to specific groups to prevent conflicts
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    // Enforce repository usage to avoid project-level repository declarations
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ProofOfConcept"  // Name of the root project
include(":app")  // Include the app module
