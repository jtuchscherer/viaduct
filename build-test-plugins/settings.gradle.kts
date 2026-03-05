@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

pluginManagement {
    includeBuild("../build-logic")
}

includeBuild("../build-logic") {
    dependencySubstitution {
        substitute(module("com.airbnb.viaduct.build:shared")).using(project(":shared"))
    }
}
