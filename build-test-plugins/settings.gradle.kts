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

// Redirect build/ dirs if viaduct.distDir is set (see gradlew).
// The .gradle/ dir is handled by a symlink created in gradlew.
val distDir = providers.systemProperty("viaduct.distDir").orNull
if (distDir != null) {
    val dist = file(distDir).resolve("build-test-plugins")
    gradle.allprojects {
        layout.buildDirectory = dist.resolve("build/${project.path.replace(":", "/")}")
    }
}
