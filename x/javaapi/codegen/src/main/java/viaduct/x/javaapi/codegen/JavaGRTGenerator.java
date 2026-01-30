package viaduct.x.javaapi.codegen;

import java.io.File;
import java.io.IOException;
import viaduct.codegen.st.STContents;
import viaduct.codegen.st.STUtilsKt;

/**
 * Combined generator for all Java GRT (GraphQL Representational Types) source files. Contains all
 * templates and generation logic in one place.
 *
 * <p>Each GraphQL type has a corresponding inner generator class:
 *
 * <ul>
 *   <li>{@link EnumGenerator} - generates Java enums from GraphQL enums
 *   <li>{@link ObjectGenerator} - generates Java classes from GraphQL object types
 *   <li>{@link InputGenerator} - generates Java classes from GraphQL input types
 *   <li>{@link InterfaceGenerator} - generates Java interfaces from GraphQL interface types
 *   <li>{@link UnionGenerator} - generates Java marker interfaces from GraphQL union types
 *   <li>{@link ResolverGenerator} - generates resolver base classes from @resolver fields
 * </ul>
 */
public final class JavaGRTGenerator {

  private JavaGRTGenerator() {
    // Static utility class
  }

  /**
   * Writes generated content to a file in the appropriate package directory.
   *
   * @param content the STContents to write
   * @param packageName the Java package name
   * @param className the class/interface name
   * @param outputDir the base output directory
   * @return the file that was written
   * @throws IOException if there's an error writing the file
   */
  private static File writeToFile(
      STContents content, String packageName, String className, File outputDir) throws IOException {
    String packagePath = packageName.replace('.', File.separatorChar);
    File packageDir = new File(outputDir, packagePath);
    if (!packageDir.exists() && !packageDir.mkdirs()) {
      throw new IOException("Failed to create directory: " + packageDir);
    }

    File outputFile = new File(packageDir, className + ".java");
    content.write(outputFile);
    return outputFile;
  }

  /** Generator for Java enums from GraphQL enum types. */
  public static final class EnumGenerator {

    private static final String TEMPLATE =
        STUtilsKt.stTemplate(
            """
            package <mdl.packageName>;

            <if(mdl.hasDescription)>
            /**
             * <mdl.description>
             */
            <endif>
            public enum <mdl.className> {
                <mdl.valueNames: {valueName | <valueName>}; separator=",
            ">
            }
            """);

    private EnumGenerator() {}

    /**
     * Generates the Java enum source code as a string.
     *
     * @param model the enum model
     * @return the generated Java source code
     */
    public static String generate(EnumModel model) {
      return new STContents(TEMPLATE, model).toString();
    }

    /**
     * Generates the Java enum source code and writes it to a file.
     *
     * @param model the enum model
     * @param outputDir the output directory
     * @return the file that was written
     * @throws IOException if there's an error writing the file
     */
    public static File generateToFile(EnumModel model, File outputDir) throws IOException {
      STContents contents = new STContents(TEMPLATE, model);
      return writeToFile(contents, model.packageName(), model.className(), outputDir);
    }
  }

  /** Generator for Java classes from GraphQL object types. */
  public static final class ObjectGenerator {

    private static final String TEMPLATE =
        STUtilsKt.stTemplate(
            """
            package <mdl.packageName>;

            import viaduct.java.api.types.GraphQLObject;
            import java.util.List;

            <if(mdl.hasDescription)>
            /**
             * <mdl.description>
             */
            <endif>
            public class <mdl.className> implements <mdl.implementsClause> {

                <mdl.fields: {f |
                private <f.javaType> <f.name>;
                }; separator="
            ">

                <mdl.fields: {f |
                public <f.javaType> <f.getterName>() {
                    return this.<f.name>;
                \\}

                public void <f.setterName>(<f.javaType> <f.name>) {
                    this.<f.name> = <f.name>;
                \\}
                }; separator="
            ">

                public static Builder builder() {
                    return new Builder();
                }

                public static class Builder {
                    <mdl.fields: {f |
                    private <f.javaType> <f.name>;
                    }; separator="
            ">

                    <mdl.fields: {f |
                    public Builder <f.name>(<f.javaType> <f.name>) {
                        this.<f.name> = <f.name>;
                        return this;
                    \\}
                    }; separator="
            ">

                    public <mdl.className> build() {
                        <mdl.className> obj = new <mdl.className>();
                        <mdl.fields: {f |
                        obj.<f.name> = this.<f.name>;
                        }; separator="
            ">
                        return obj;
                    }
                }
            }
            """);

    private ObjectGenerator() {}

    /**
     * Generates the Java class source code as a string.
     *
     * @param model the object model
     * @return the generated Java source code
     */
    public static String generate(ObjectModel model) {
      return new STContents(TEMPLATE, model).toString();
    }

    /**
     * Generates the Java class source code and writes it to a file.
     *
     * @param model the object model
     * @param outputDir the output directory
     * @return the file that was written
     * @throws IOException if there's an error writing the file
     */
    public static File generateToFile(ObjectModel model, File outputDir) throws IOException {
      STContents contents = new STContents(TEMPLATE, model);
      return writeToFile(contents, model.packageName(), model.className(), outputDir);
    }
  }

  /** Generator for Java classes from GraphQL input types. */
  public static final class InputGenerator {

    private static final String TEMPLATE =
        STUtilsKt.stTemplate(
            """
            package <mdl.packageName>;

            import viaduct.java.api.types.GraphQLInput;
            import java.util.List;

            <if(mdl.hasDescription)>
            /**
             * <mdl.description>
             */
            <endif>
            public class <mdl.className> implements GraphQLInput {

                <mdl.fields: {f |
                private <f.javaType> <f.name>;
                }; separator="\\n">

                <mdl.fields: {f |
                public <f.javaType> <f.getterName>() {
                    return this.<f.name>;
                \\}

                public void <f.setterName>(<f.javaType> <f.name>) {
                    this.<f.name> = <f.name>;
                \\}
                }; separator="\\n">

                public static Builder builder() {
                    return new Builder();
                }

                public static class Builder {
                    <mdl.fields: {f |
                    private <f.javaType> <f.name>;
                    }; separator="\\n">

                    <mdl.fields: {f |
                    public Builder <f.name>(<f.javaType> <f.name>) {
                        this.<f.name> = <f.name>;
                        return this;
                    \\}
                    }; separator="\\n">

                    public <mdl.className> build() {
                        <mdl.className> obj = new <mdl.className>();
                        <mdl.fields: {f |
                        obj.<f.name> = this.<f.name>;
                        }; separator="\\n">
                        return obj;
                    }
                }
            }
            """);

    private InputGenerator() {}

    /**
     * Generates the Java class source code as a string.
     *
     * @param model the input model
     * @return the generated Java source code
     */
    public static String generate(InputModel model) {
      return new STContents(TEMPLATE, model).toString();
    }

    /**
     * Generates the Java class source code and writes it to a file.
     *
     * @param model the input model
     * @param outputDir the output directory
     * @return the file that was written
     * @throws IOException if there's an error writing the file
     */
    public static File generateToFile(InputModel model, File outputDir) throws IOException {
      STContents contents = new STContents(TEMPLATE, model);
      return writeToFile(contents, model.packageName(), model.className(), outputDir);
    }
  }

  /** Generator for Java interfaces from GraphQL interface types. */
  public static final class InterfaceGenerator {

    private static final String TEMPLATE =
        STUtilsKt.stTemplate(
            """
            package <mdl.packageName>;

            import viaduct.java.api.types.GraphQLInterface;
            import java.util.List;

            <if(mdl.hasDescription)>
            /**
             * <mdl.description>
             */
            <endif>
            public interface <mdl.className> extends <mdl.extendsClause> {

                <mdl.fields: {f |
                <f.javaType> <f.getterName>();
                }; separator="\\n">
            }
            """);

    private InterfaceGenerator() {}

    /**
     * Generates the Java interface source code as a string.
     *
     * @param model the interface model
     * @return the generated Java source code
     */
    public static String generate(InterfaceModel model) {
      return new STContents(TEMPLATE, model).toString();
    }

    /**
     * Generates the Java interface source code and writes it to a file.
     *
     * @param model the interface model
     * @param outputDir the output directory
     * @return the file that was written
     * @throws IOException if there's an error writing the file
     */
    public static File generateToFile(InterfaceModel model, File outputDir) throws IOException {
      STContents contents = new STContents(TEMPLATE, model);
      return writeToFile(contents, model.packageName(), model.className(), outputDir);
    }
  }

  /** Generator for Java union interfaces from GraphQL union types. */
  public static final class UnionGenerator {

    private static final String TEMPLATE =
        STUtilsKt.stTemplate(
            """
            package <mdl.packageName>;

            import viaduct.java.api.types.GraphQLUnion;

            /**
            <if(mdl.hasDescription)>
             * <mdl.description>
             *
            <endif>
             * Possible types: <mdl.memberTypes; separator=", ">
             */
            public interface <mdl.className> extends GraphQLUnion {
            }
            """);

    private UnionGenerator() {}

    /**
     * Generates the Java union interface source code as a string.
     *
     * @param model the union model
     * @return the generated Java source code
     */
    public static String generate(UnionModel model) {
      return new STContents(TEMPLATE, model).toString();
    }

    /**
     * Generates the Java union interface source code and writes it to a file.
     *
     * @param model the union model
     * @param outputDir the output directory
     * @return the file that was written
     * @throws IOException if there's an error writing the file
     */
    public static File generateToFile(UnionModel model, File outputDir) throws IOException {
      STContents contents = new STContents(TEMPLATE, model);
      return writeToFile(contents, model.packageName(), model.className(), outputDir);
    }
  }

  /**
   * Generator for Java resolver base classes from GraphQL fields with @resolver directive.
   *
   * <p>Generates abstract base classes that tenant developers extend to implement field resolvers.
   * Each resolver class is annotated with @ResolverFor and contains a Context inner class that
   * delegates to FieldExecutionContext.
   */
  public static final class ResolverGenerator {

    // Main template - uses pre-formatted type strings to avoid angle bracket escaping issues
    private static final String MAIN_TEMPLATE =
        STUtilsKt.stTemplate(
            """
            package <mdl.packageName>.resolverbases;

            import java.util.List;
            import java.util.Map;
            import java.util.concurrent.CompletableFuture;
            import viaduct.java.api.annotations.ResolverFor;
            import viaduct.java.api.context.FieldExecutionContext;
            import viaduct.java.api.globalid.GlobalID;
            import viaduct.java.api.reflect.Type;
            import viaduct.java.api.resolvers.FieldResolverBase;
            import viaduct.java.api.types.Arguments;
            import viaduct.java.api.types.CompositeOutput;
            import viaduct.java.api.types.NodeCompositeOutput;

            /**
             * Generated resolver base classes for <mdl.typeName> type.
             */
            public final class <mdl.typeName>Resolvers {

                private <mdl.typeName>Resolvers() {
                    // Utility class
                }

                <mdl.resolvers:{r |
                @ResolverFor(typeName = "<r.gqlTypeName>", fieldName = "<r.gqlFieldName>")
                public abstract static class <r.resolverClassName>
                    implements <r.fieldResolverBaseType> {

                    /**
                     * Context for <r.gqlTypeName>.<r.gqlFieldName> resolver.
                     * Provides type-safe access to object value, query value, arguments, and selections.
                     */
                    public static class Context
                        implements <r.contextBaseType> {

                        private final <r.fieldExecutionContextType> inner;

                        public Context(<r.fieldExecutionContextType> inner) {
                            this.inner = inner;
                        \\}

                        @Override
                        public <r.objectType> getObjectValue() {
                            return inner.getObjectValue();
                        \\}

                        @Override
                        public <r.queryType> getQueryValue() {
                            return inner.getQueryValue();
                        \\}

                        @Override
                        public <r.argumentsType> getArguments() {
                            return inner.getArguments();
                        \\}

                        @Override
                        public Object getSelections() {
                            return inner.getSelections();
                        \\}

                        @Override
                        public \\<T extends NodeCompositeOutput> GlobalID\\<T> globalIDFor(Type\\<T> type, String internalID) {
                            return inner.globalIDFor(type, internalID);
                        \\}

                        @Override
                        public \\<T extends NodeCompositeOutput> String serialize(GlobalID\\<T> globalID) {
                            return inner.serialize(globalID);
                        \\}

                        @Override
                        public Object getRequestContext() {
                            return inner.getRequestContext();
                        \\}

                        @Override
                        public \\<T extends NodeCompositeOutput> T nodeFor(GlobalID\\<T> id) {
                            return inner.nodeFor(id);
                        \\}
                    \\}

                    /**
                     * Resolves the <r.gqlFieldName> field value for a single parent object.
                     *
                     * @param ctx the execution context
                     * @return a future that completes with the resolved value
                     */
                    public <r.resolveFutureType> resolve(Context ctx) {
                        throw new UnsupportedOperationException(
                            "<r.gqlTypeName>.<r.gqlFieldName>.resolve not implemented");
                    \\}
                    <if(r.includeBatchResolve)>

                    /**
                     * Resolves the <r.gqlFieldName> field values for multiple parent objects in a batch.
                     *
                     * @param contexts list of execution contexts
                     * @return a future that completes with a map from context to resolved value
                     */
                    public <r.batchResolveFutureType> batchResolve(<r.batchResolveContextListType> contexts) {
                        throw new UnsupportedOperationException(
                            "<r.gqlTypeName>.<r.gqlFieldName>.batchResolve not implemented");
                    \\}
                    <endif>
                \\}
                }; separator="\\n">
            }
            """);

    private ResolverGenerator() {}

    /**
     * Generates the Java resolvers source code as a string.
     *
     * @param model the resolvers file model
     * @return the generated Java source code
     */
    public static String generate(ResolversFileModel model) {
      return new STContents(MAIN_TEMPLATE, model).toString();
    }

    /**
     * Generates the Java resolvers source code and writes it to a file.
     *
     * <p>Resolver files are written to package subdirectories under the output directory. The
     * package is {tenantPackage}.resolverbases, so the file path will be:
     * {resolverGeneratedDir}/{tenantPackage/path}/resolverbases/{TypeName}Resolvers.java
     *
     * @param model the resolvers file model
     * @param resolverGeneratedDir the output directory
     * @return the file that was written
     * @throws IOException if there's an error writing the file
     */
    public static File generateToFile(ResolversFileModel model, File resolverGeneratedDir)
        throws IOException {
      STContents contents = new STContents(MAIN_TEMPLATE, model);
      // Create package directory: {packageName}.resolverbases
      String fullPackage = model.packageName() + ".resolverbases";
      String packagePath = fullPackage.replace('.', File.separatorChar);
      File packageDir = new File(resolverGeneratedDir, packagePath);
      if (!packageDir.exists() && !packageDir.mkdirs()) {
        throw new IOException("Failed to create directory: " + packageDir);
      }
      File outputFile = new File(packageDir, model.typeName() + "Resolvers.java");
      contents.write(outputFile);
      return outputFile;
    }
  }
}
