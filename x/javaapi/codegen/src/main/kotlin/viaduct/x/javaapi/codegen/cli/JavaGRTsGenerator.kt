package viaduct.x.javaapi.codegen.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import viaduct.x.javaapi.codegen.JavaGRTsCodegen

/**
 * CLI entry point for generating Java GRTs (GraphQL Representational Types) from GraphQL schemas.
 *
 * Parameters match Kotlin's Args pattern:
 * - grtOutputDir: directory for GRT files (written to package subdirs)
 * - grtPackage: package name for GRT types
 * - resolverGeneratedDir: directory for resolver files (written directly, not in package subdirs)
 * - tenantPackage: package name for resolver bases ({tenantPackage}.resolverbases)
 */
class JavaGRTsGenerator : CliktCommand(
    name = "java-grts-generator",
    help = "Generates Java GRTs (GraphQL Representational Types) from GraphQL schema files"
) {
    private val schemaFiles: List<File> by option("--schema_files", help = "Comma-separated list of GraphQL schema files")
        .file(mustExist = true, canBeDir = false)
        .split(",")
        .required()

    private val grtOutputDir: File by option("--grt_output_dir", help = "Output directory for generated GRT files (written to package subdirs)")
        .file(mustExist = false, canBeFile = false)
        .required()

    private val grtPackage: String by option("--grt_package", help = "Java package name for generated GRT types")
        .required()

    private val resolverGeneratedDir: File by option("--resolver_generated_dir", help = "Output directory for resolver files (written directly, not in package subdirs)")
        .file(mustExist = false, canBeFile = false)
        .required()

    private val tenantPackage: String by option("--tenant_package", help = "Java package name for resolver bases (uses {tenantPackage}.resolverbases)")
        .required()

    private val verbose: Boolean by option("--verbose", help = "Print generation results")
        .flag()

    override fun run() {
        val codegen = JavaGRTsCodegen()
        val result = codegen.generate(schemaFiles, grtOutputDir, grtPackage, resolverGeneratedDir, tenantPackage)

        if (verbose) {
            for (file in result.generatedFiles()) {
                echo("Generated: $file")
            }

            echo("Generated ${result.totalCount()} types:")
            echo("  GRT output: ${grtOutputDir.absolutePath}")
            echo("  Resolver output: ${resolverGeneratedDir.absolutePath}")
            echo("  - ${result.enumCount()} enum(s)")
            echo("  - ${result.objectCount()} object(s)")
            echo("  - ${result.inputCount()} input(s)")
            echo("  - ${result.interfaceCount()} interface(s)")
            echo("  - ${result.unionCount()} union(s)")
            echo("  - ${result.resolverFileCount()} resolver file(s) containing ${result.resolverCount()} resolver(s)")
        }
    }

    object Main {
        @JvmStatic
        fun main(args: Array<String>) = JavaGRTsGenerator().main(args)
    }
}
