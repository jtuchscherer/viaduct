@file:Suppress("ForbiddenImport")

package viaduct.java.runtime.fixtures

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import viaduct.engine.api.FieldResolverExecutor
import viaduct.engine.api.mocks.MockTenantAPIBootstrapper
import viaduct.java.runtime.bridge.DefaultJavaResolverClassFinder
import viaduct.java.runtime.bridge.JavaModuleBootstrapper
import viaduct.service.ViaductBuilder
import viaduct.service.api.ExecutionInput
import viaduct.service.api.ExecutionResult
import viaduct.service.api.SchemaId
import viaduct.service.api.mocks.MockTenantAPIBootstrapperBuilder
import viaduct.service.api.spi.TenantCodeInjector
import viaduct.service.api.spi.mocks.MockFlagManager
import viaduct.service.runtime.SchemaConfiguration
import viaduct.service.runtime.StandardViaduct

/**
 * Base class for testing Java GraphQL feature applications with Viaduct.
 *
 * This class provides a test harness for Java resolvers, similar to the Kotlin
 * [viaduct.tenant.runtime.fixtures.FeatureAppTestBase] but for Java resolver implementations.
 *
 * Usage:
 * 1. Extend this class in your Java test
 * 2. Override the `getSdl()` method with your GraphQL schema
 * 3. Define your resolver implementations as inner classes annotated with @Resolver
 * 4. Use the `execute()` method to run queries against your implementation
 *
 * Example:
 * ```java
 * public class MyFeatureAppTest extends JavaFeatureAppTestBase {
 *     @Override
 *     public String getSdl() {
 *         return """
 *             extend type Query {
 *                 hello: String! @resolver
 *             }
 *         """;
 *     }
 *
 *     @Resolver
 *     public static class HelloResolver extends QueryResolvers.Hello {
 *         @Override
 *         public CompletableFuture<String> resolve(Context ctx) {
 *             return CompletableFuture.completedFuture("Hello, World!");
 *         }
 *     }
 *
 *     @Test
 *     public void testHelloQuery() {
 *         ExecutionResult result = execute("{ hello }");
 *         // Assert on result
 *     }
 * }
 * ```
 *
 * **Important**: GRTs are created per package namespace. Multiple tests in the same package will
 * share the same generated classes, which can cause conflicts. To avoid this, place each feature
 * test app in its own separate package.
 */
abstract class JavaFeatureAppTestBase {
    /**
     * Override this method to provide the GraphQL schema for your test.
     */
    abstract fun getSdl(): String

    private val flagManager = MockFlagManager()

    // Package name of the derived class (e.g., "...enum_example.resolvers")
    private val derivedClassPackage: String =
        this::class.java.`package`?.name ?: throw RuntimeException(
            "Unable to read package name from subclass ${this::class.simpleName}"
        )

    // Parent package prefix covering both "resolvers" and "resolverbases" sibling packages
    // e.g., "viaduct.java.runtime.featureapp.enum_example" from "...enum_example.resolvers"
    private val featureAppPackagePrefix: String =
        derivedClassPackage.substringBeforeLast('.')

    // Class finder for discovering Java resolvers in the feature app's packages
    private val classFinder by lazy {
        DefaultJavaResolverClassFinder(
            tenantPackage = featureAppPackagePrefix,
            grtPackagePrefix = "$featureAppPackagePrefix.grt"
        )
    }

    // Bootstrapper for Java resolvers
    private val bootstrapper by lazy {
        JavaModuleBootstrapper(classFinder, TenantCodeInjector.Naive)
    }

    protected lateinit var viaductBuilder: ViaductBuilder
    protected lateinit var viaductSchemaConfiguration: SchemaConfiguration
    protected lateinit var viaductService: StandardViaduct

    /**
     * Allows customization of the ViaductBuilder before building the service.
     */
    fun withViaductBuilder(builderUpdate: ViaductBuilder.() -> Unit) {
        viaductBuilder.apply(builderUpdate)
    }

    /**
     * Sets a custom schema configuration.
     */
    fun withSchemaConfiguration(config: SchemaConfiguration) {
        viaductBuilder = viaductBuilder.withSchemaConfiguration(config)
        viaductSchemaConfiguration = config
    }

    @BeforeEach
    open fun initViaductBuilder() {
        if (!::viaductBuilder.isInitialized) {
            val tenantAPIBootstrapper = MockTenantAPIBootstrapper(listOf(bootstrapper))
            viaductBuilder = ViaductBuilder()
                .withFlagManager(flagManager)
                .withTenantAPIBootstrapperBuilder(MockTenantAPIBootstrapperBuilder(tenantAPIBootstrapper))
        }
    }

    /**
     * Executes a query against the test application.
     *
     * @param query The GraphQL query to execute.
     * @param variables The variables to use for the query.
     * @param schemaId The schema ID to use.
     * @param requestContext Optional request context.
     *
     * @return The result of the query execution.
     */
    @JvmOverloads
    open fun execute(
        query: String,
        variables: Map<String, Any?> = mapOf(),
        schemaId: SchemaId = defaultSchemaId(),
        requestContext: Any? = null,
    ): ExecutionResult {
        return runBlocking {
            tryBuildViaductService()
            val executionInput = ExecutionInput.create(
                operationText = query,
                variables = variables,
                requestContext = requestContext,
            )
            val result = viaductService.executeAsync(executionInput, schemaId).await()
            result
        }
    }

    /**
     * Returns the default schema ID for queries.
     */
    open fun defaultSchemaId(): SchemaId = SchemaId.Full

    /**
     * Returns custom scope configuration. Override to provide custom scopes.
     */
    open fun getScopeConfig(): Set<SchemaConfiguration.ScopeConfig> = emptySet()

    /**
     * Returns the field resolver executor for a given type and field.
     * Useful for testing that required selections are properly wired.
     *
     * @param typeName The GraphQL type name (e.g., "Person")
     * @param fieldName The field name (e.g., "fullAddress")
     * @return The FieldResolverExecutor if found, null otherwise
     */
    protected fun getFieldResolverExecutor(
        typeName: String,
        fieldName: String
    ): FieldResolverExecutor? {
        tryBuildViaductService()
        val schema = viaductService.engineRegistry.getSchema(SchemaId.Full)
        val executors = bootstrapper.fieldResolverExecutors(schema)
        return executors.find { (coordinate, _) ->
            coordinate.first == typeName && coordinate.second == fieldName
        }?.second
    }

    /**
     * Attempts to build the [StandardViaduct] instance if it has not been initialized yet.
     */
    @Suppress("TooGenericExceptionCaught")
    fun tryBuildViaductService() {
        if (!::viaductSchemaConfiguration.isInitialized) {
            viaductSchemaConfiguration = SchemaConfiguration.fromSdl(getSdl(), scopes = getScopeConfig())
            viaductBuilder.withSchemaConfiguration(viaductSchemaConfiguration)
        }
        if (!::viaductService.isInitialized) {
            try {
                viaductService = viaductBuilder.build()
            } catch (t: Throwable) {
                throw RuntimeException("Failed to build Viaduct service", t)
            }
        }
    }
}
