package conventions

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

tasks.withType<KotlinCompile>().configureEach {
    val taskName = name.lowercase()

    // Skip only integrationTest and testFixtures compilations.
    // Everything else (main, test, jmh, etc.) will get the opt-ins.
    val isIntegrationOrFixtures =
        taskName.contains("integrationtest") ||
                taskName.contains("testfixtures")

    if (!isIntegrationOrFixtures) {
        compilerOptions {
            optIn.add("viaduct.apiannotations.ExperimentalApi")
            optIn.add("viaduct.apiannotations.InternalApi")
            optIn.add("viaduct.apiannotations.VisibleForTest")
        }
    }
}
