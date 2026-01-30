package viaduct.x.javaapi.codegen.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import viaduct.codegen.km.ctdiff.ClassDiff;
import viaduct.codegen.km.ctdiff.ClassFinder;
import viaduct.codegen.km.ctdiff.JavaClassLoaderClassFinder;
import viaduct.x.javaapi.codegen.JavaGRTsCodegen;

/**
 * ClassDiff tests for generated enum classes, comparing them against manually built expected
 * classes.
 *
 * <p>The test flow:
 *
 * <ol>
 *   <li>Load GraphQL schema from test resources
 *   <li>Generate Java source files using JavaGRTsCodegen
 *   <li>Compile the generated source files
 *   <li>Load the compiled classes with a custom ClassLoader
 *   <li>Use ClassDiff with JavaClassLoaderClassFinder to compare generated vs expected classes
 * </ol>
 */
class EnumClassDiffTest {

  private static final String GENERATED_PACKAGE = "viaduct.x.javaapi.codegen.exercise.generated";
  private static final String EXPECTED_PACKAGE = "viaduct.x.javaapi.codegen.exercise.grts";
  private static final String SCHEMA_RESOURCE = "graphql/exerciser_enum_schema.graphqls";

  @TempDir Path tempDir;

  private JavaGRTsCodegen codegen;
  private viaduct.invariants.InvariantChecker diffs;

  @BeforeEach
  void setUp() {
    codegen = new JavaGRTsCodegen();
    diffs = new viaduct.invariants.InvariantChecker();
  }

  @Test
  void exerciseAllEnums() throws Exception {
    // Step 1: Load schema from resources
    File schemaFile = extractSchemaToTempFile();

    // Step 2: Generate Java source files
    Path generatedSourceDir = tempDir.resolve("generated-sources");
    Files.createDirectories(generatedSourceDir);

    codegen.generate(List.of(schemaFile), generatedSourceDir.toFile(), GENERATED_PACKAGE);

    // Step 3: Compile the generated source files
    Path compiledClassesDir = tempDir.resolve("compiled-classes");
    Files.createDirectories(compiledClassesDir);

    compileGeneratedSources(generatedSourceDir, compiledClassesDir);

    // Step 4: Create a ClassLoader for the compiled classes
    URLClassLoader generatedClassLoader =
        new URLClassLoader(
            new URL[] {compiledClassesDir.toUri().toURL()}, getClass().getClassLoader());

    // Step 5: Create ClassFinder and ClassDiff - NO Javassist dependency!
    ClassFinder classFinder = new JavaClassLoaderClassFinder(generatedClassLoader);
    ClassDiff classDiff = new ClassDiff(EXPECTED_PACKAGE, GENERATED_PACKAGE, diffs, classFinder);

    // Exercise all enums defined in the schema
    List<String> enumNames =
        List.of("SimpleEnum", "EnumWithDescription", "SingleValueEnum", "StatusEnum");

    for (String enumName : enumNames) {
      Class<?> expectedClass = Class.forName(EXPECTED_PACKAGE + "." + enumName);
      Class<?> generatedClass = classFinder.find(GENERATED_PACKAGE + "." + enumName);
      classDiff.compare(expectedClass, generatedClass);
    }

    // Report results
    if (!diffs.isEmpty()) {
      StringBuilder result = new StringBuilder("Exerciser failures:\n");
      diffs.toMultilineString(result);
      System.err.println(result);
    }

    assertThat(diffs.isEmpty())
        .withFailMessage(
            "Expected no failures, but found:\n%s", String.join("\n", diffs.toListOfErrors()))
        .isTrue();
  }

  @Test
  void exerciseSimpleEnum() throws Exception {
    exerciseSingleEnum("SimpleEnum");
  }

  @Test
  void exerciseEnumWithDescription() throws Exception {
    exerciseSingleEnum("EnumWithDescription");
  }

  @Test
  void exerciseSingleValueEnum() throws Exception {
    exerciseSingleEnum("SingleValueEnum");
  }

  @Test
  void exerciseStatusEnum() throws Exception {
    exerciseSingleEnum("StatusEnum");
  }

  private void exerciseSingleEnum(String enumName) throws Exception {
    // Load schema from resources
    File schemaFile = extractSchemaToTempFile();

    // Generate Java source files
    Path generatedSourceDir = tempDir.resolve("generated-sources");
    Files.createDirectories(generatedSourceDir);

    codegen.generate(List.of(schemaFile), generatedSourceDir.toFile(), GENERATED_PACKAGE);

    // Compile the generated source files
    Path compiledClassesDir = tempDir.resolve("compiled-classes");
    Files.createDirectories(compiledClassesDir);

    compileGeneratedSources(generatedSourceDir, compiledClassesDir);

    // Create a ClassLoader for the compiled classes
    URLClassLoader generatedClassLoader =
        new URLClassLoader(
            new URL[] {compiledClassesDir.toUri().toURL()}, getClass().getClassLoader());

    // Create ClassFinder and ClassDiff - NO Javassist dependency!
    ClassFinder classFinder = new JavaClassLoaderClassFinder(generatedClassLoader);
    ClassDiff classDiff = new ClassDiff(EXPECTED_PACKAGE, GENERATED_PACKAGE, diffs, classFinder);

    // Load and compare classes
    Class<?> expectedClass = Class.forName(EXPECTED_PACKAGE + "." + enumName);
    Class<?> generatedClass = classFinder.find(GENERATED_PACKAGE + "." + enumName);
    classDiff.compare(expectedClass, generatedClass);

    // Report results
    if (!diffs.isEmpty()) {
      StringBuilder result = new StringBuilder("Exerciser failures for " + enumName + ":\n");
      diffs.toMultilineString(result);
      System.err.println(result);
    }

    assertThat(diffs.isEmpty())
        .withFailMessage(
            "Expected no failures for %s, but found:\n%s",
            enumName, String.join("\n", diffs.toListOfErrors()))
        .isTrue();
  }

  private File extractSchemaToTempFile() throws IOException {
    InputStream schemaStream = getClass().getClassLoader().getResourceAsStream(SCHEMA_RESOURCE);
    if (schemaStream == null) {
      throw new IOException("Schema resource not found: " + SCHEMA_RESOURCE);
    }

    Path schemaFile = tempDir.resolve("schema.graphqls");
    Files.copy(schemaStream, schemaFile);
    return schemaFile.toFile();
  }

  private void compileGeneratedSources(Path sourceDir, Path outputDir) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException(
          "No Java compiler available. Make sure you're running with a JDK, not just a JRE.");
    }

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, null)) {

      // Find all generated .java files
      List<File> javaFiles =
          Files.walk(sourceDir)
              .filter(p -> p.toString().endsWith(".java"))
              .map(Path::toFile)
              .toList();

      if (javaFiles.isEmpty()) {
        throw new IOException("No Java files found in " + sourceDir);
      }

      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(javaFiles);

      // Set compilation options
      List<String> options = Arrays.asList("-d", outputDir.toString());

      JavaCompiler.CompilationTask task =
          compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

      boolean success = task.call();

      if (!success) {
        StringBuilder errorMessage = new StringBuilder("Compilation failed:\n");
        diagnostics
            .getDiagnostics()
            .forEach(
                d ->
                    errorMessage
                        .append(d.getKind())
                        .append(": ")
                        .append(d.getMessage(null))
                        .append("\n"));
        throw new IOException(errorMessage.toString());
      }
    }
  }
}
