plugins {
    `kotlin-dsl`
    id("conventions.kotlin")
    id("conventions.kotlin-static-analysis")
    id("com.gradle.plugin-publish") version "2.0.0"
    id("conventions.viaduct-publishing")
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":common"))

    // Your runtime helpers used by the plugin implementation (keep as needed)
    implementation(libs.viaduct.tenant.codegen)
    implementation(libs.viaduct.shared.graphql)

    // Do NOT leak the Kotlin Gradle Plugin at runtime
    compileOnly(libs.kotlin.gradle.plugin)

    // Serve runtime dependencies (bundled into the plugin)
    implementation(libs.viaduct.service.api)
    implementation(libs.viaduct.service.wiring)
    implementation(libs.viaduct.tenant.api)
    implementation(libs.graphql.java)

    // Ktor server for development server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.jackson)

    // Classpath scanning for finding @ViaductServerConfiguration
    implementation(libs.classgraph)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core.jvm)

    // JSON
    implementation(libs.jackson.module)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.engine)
}

// Manifest with Implementation-Version for runtime access if you need it
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString()
        )
    }
}

gradlePlugin {
    website = "https://viaduct.airbnb.tech"
    vcsUrl = "https://github.com/airbnb/viaduct"

    plugins {
        create("viaductApplication") {
            // e.g., com.airbnb.viaduct.application-gradle-plugin
            id = "$group.application-gradle-plugin"
            implementationClass = "viaduct.gradle.ViaductApplicationPlugin"
            displayName = "Viaduct :: Application Plugin"
            description = "Application plugin for Viaduct-based apps."
            tags.set(listOf("viaduct", "graphql", "kotlin"))
        }
    }
}

viaductPublishing {
    name.set("Application Gradle Plugin")
    description.set("Gradle plugin for Viaduct application projects.")
    artifactId.set("application-gradle-plugin")
}
