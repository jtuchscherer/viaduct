plugins {
    `kotlin-dsl`
    id("conventions.kotlin-static-analysis")
}

dependencies {
    implementation(libs.viaduct.build.shared)

    // Do NOT leak the Kotlin Gradle Plugin at runtime
    compileOnly(libs.kotlin.gradle.plugin)
}
