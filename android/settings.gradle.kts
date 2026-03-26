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

rootProject.name = "Encore"

include(":app")
include(":core:ui")
include(":core:data")
include(":core:sync")
include(":feature:auth")
include(":feature:library")
include(":feature:setlists")
include(":feature:performance")
include(":feature:edit")
