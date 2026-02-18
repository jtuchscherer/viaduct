@file:Suppress("ForbiddenImport")

package viaduct.tenant.runtime.fixtures

import com.google.inject.Guice
import com.google.inject.Injector
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import viaduct.api.Resolver
import viaduct.api.bootstrap.ViaductTenantAPIBootstrapper
import viaduct.api.internal.NodeResolverFor
import viaduct.api.internal.ResolverFor
import viaduct.api.reflect.Type
import viaduct.api.types.NodeObject
import viaduct.service.ViaductBuilder
import viaduct.service.api.ExecutionInput
import viaduct.service.api.ExecutionResult
import viaduct.service.api.SchemaId
import viaduct.service.api.spi.globalid.GlobalIDCodecDefault
import viaduct.service.api.spi.mocks.MockFlagManager
import viaduct.service.runtime.SchemaConfiguration
import viaduct.service.runtime.StandardViaduct
import viaduct.tenant.runtime.bootstrap.GuiceTenantCodeInjector
import viaduct.tenant.runtime.bootstrap.ViaductTenantResolverClassFinderFactory

/**
 * Base class for testing GraphQL feature applications with Viaduct.
 *
 * Usage:
 * 1. Extend this class in your test
 * 2. Override the `sdl` property with your GraphQL schema (between #START_SCHEMA and #END_SCHEMA markers)
 * 3. Override the `customScalar` property with the set of custom scalars defined in the schema
 * 4. Define your resolver implementations as inner classes annotated with @Resolver
 * 5. Use the `execute()` method to run queries against your implementation
 *
 * Example:
 * ```kotlin
 * class MyFeatureAppTest : FeatureAppTestBase() {
 *     override var sdl = """
 *         |#START_SCHEMA <- schema start marker needed
 *         | type Query {
 *         |    hello: String @resolver
 *         | }
 *         |#END_SCHEMA <- schema end marker needed
 *     """.trimMargin()
 *
 *     @Resolver
 *     class Query_HelloResolver : QueryResolvers.Hello() {
 *         override suspend fun resolve(ctx: Context) = "Hello, World!"
 *     }
 *
 *     @Test
 *     fun testHelloQuery() {
 *         val result = execute("test", "{ hello }")
 *         // Assert on result
 *     }
 * }
 *
 * **Important**: GRTs are created per package namespace. Multiple tests in the same package will
 * share the same generated classes, which can cause conflicts. To avoid this, place each feature
 * test app in its own separate package.
 *```
 */
abstract class FeatureAppTestBase {
    open lateinit var sdl: String
        protected set

    /**
     * When true, validates before build that all schema-declared resolvers have corresponding
     * @Resolver-annotated implementation classes. This catches the common mistake of declaring
     * @resolver in the schema but forgetting to implement the resolver class.
     *
     * Override to false in tests that intentionally omit resolver implementations
     * (e.g., tenant filtering tests).
     */
    protected open val validateResolverCompleteness: Boolean = true

    private val injector: Injector by lazy { Guice.createInjector() }
    private val guiceTenantCodeInjector by lazy { GuiceTenantCodeInjector(injector) }
    private val flagManager = MockFlagManager()

    // GlobalID codec for creating GlobalID strings in tests
    private val globalIdCodec = GlobalIDCodecDefault

    // package name of the derived class
    private val derivedClassPackagePrefix: String =
        this::class.java.`package`?.name ?: throw RuntimeException(
            "Unable to read package name from subclass ${this::class.simpleName}"
        )

    // resolver class finder factory for feature test app use case
    private val tenantResolverClassFinderFactory = ViaductTenantResolverClassFinderFactory(
        grtPackagePrefix = derivedClassPackagePrefix
    )

    protected val viaductTenantAPIBootstrapperBuilder =
        ViaductTenantAPIBootstrapper.Builder()
            .tenantCodeInjector(guiceTenantCodeInjector)
            .tenantResolverClassFinderFactory(tenantResolverClassFinderFactory)
            .tenantPackagePrefix(derivedClassPackagePrefix)

    protected lateinit var viaductBuilder: ViaductBuilder
    lateinit var viaductSchemaConfiguration: SchemaConfiguration
    lateinit var viaductService: StandardViaduct

    fun withViaductBuilder(builderUpdate: ViaductBuilder.() -> Unit) {
        viaductBuilder.apply(builderUpdate)
    }

    fun withSchemaConfiguration(config: SchemaConfiguration) {
        viaductBuilder = viaductBuilder.withSchemaConfiguration(config)
        viaductSchemaConfiguration = config
    }

    @BeforeEach
    open fun initViaductBuilder() {
        if (!::viaductBuilder.isInitialized) {
            viaductBuilder = ViaductBuilder()
                .withFlagManager(flagManager)
                .withTenantAPIBootstrapperBuilder(viaductTenantAPIBootstrapperBuilder)
        }
    }

    /**
     * Creates a GlobalID string for the given type and internal ID.
     * This is a helper method to avoid repeating ctx.globalIDStringFor() calls in tests.
     * This method can be accessed from resolver classes to generate GlobalIDs outside of Viaduct context.
     *
     * @param type The type reflection object (e.g., Foo.Reflection)
     * @param internalId The internal ID to create a GlobalID for
     * @return A GlobalID string
     */
    fun <T : NodeObject> createGlobalIdString(
        type: Type<T>,
        internalId: String
    ): String = globalIdCodec.serialize(type.name, internalId)

    /**
     * Helper function to get internalId from a GlobalID string.
     */
    fun <T : NodeObject> getInternalId(globalID: String): String {
        val (_, internalId) = globalIdCodec.deserialize(globalID)
        return internalId
    }

    /**
     * Executes a query against the test application.
     *
     * @param scopeId The scope ID to use for the query.
     * @param query The query to execute.
     * @param variables The variables to use for the query.
     *
     * @return The result of the query execution.
     */
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

    open fun defaultSchemaId(): SchemaId = SchemaId.Full

    open fun getScopeConfig(): Set<SchemaConfiguration.ScopeConfig> = emptySet()

    /**
     * Attempts to build the [StandardViaduct] instance if it has not been initialized yet.
     */
    @Suppress("TooGenericExceptionCaught")
    fun tryBuildViaductService() {
        if (!::viaductSchemaConfiguration.isInitialized) {
            viaductSchemaConfiguration = SchemaConfiguration.fromSdl(sdl, scopes = getScopeConfig())
            viaductBuilder.withSchemaConfiguration(viaductSchemaConfiguration)
        }
        if (!::viaductService.isInitialized) {
            if (validateResolverCompleteness) {
                validateResolverImplementations()
            }
            try {
                viaductService = viaductBuilder.build()
            } catch (t: Throwable) {
                throw RuntimeException("Failed to build Viaduct service", t)
            }
        }
    }

    /**
     * Validates that all generated resolver base classes have corresponding implementation classes.
     *
     * When the schema declares @resolver on a field or type, codegen generates a base class
     * (annotated with @ResolverFor or @NodeResolverFor). This method checks that each base class
     * has at least one implementation:
     * - Field resolvers: must have a subclass annotated with @Resolver
     * - Node resolvers: must have at least one subclass
     *
     * Without this check, a missing resolver is silently skipped during bootstrapping and the
     * field returns null at query time with no indication of what went wrong.
     */
    private fun validateResolverImplementations() {
        val classFinder = tenantResolverClassFinderFactory.create(derivedClassPackagePrefix)
        val missingResolvers = mutableListOf<String>()

        // Check field resolvers (@ResolverFor base classes need a @Resolver subclass).
        // Exclude built-in Viaduct resolvers (Query.node, Query.nodes) that are provided
        // by ViaductNodeResolverAPIBootstrapper rather than by tenant @Resolver classes.
        val builtInResolverFields = setOf("Query" to "node", "Query" to "nodes")
        for (baseClass in classFinder.resolverClassesInPackage()) {
            val annotation = baseClass.annotations.firstOrNull { it is ResolverFor } as? ResolverFor
                ?: continue
            if ((annotation.typeName to annotation.fieldName) in builtInResolverFields) continue
            val implementations = classFinder.getSubTypesOf(baseClass)
                .filter { it.isAnnotationPresent(Resolver::class.java) }
            if (implementations.isEmpty()) {
                missingResolvers.add("${annotation.typeName}.${annotation.fieldName}")
            }
        }

        // Check node resolvers (@NodeResolverFor base classes need any subclass)
        for (baseClass in classFinder.nodeResolverForClassesInPackage()) {
            val annotation = baseClass.annotations.firstOrNull { it is NodeResolverFor } as? NodeResolverFor
                ?: continue
            val implementations = classFinder.getSubTypesOf(baseClass)
            if (implementations.isEmpty()) {
                missingResolvers.add("Node(${annotation.typeName})")
            }
        }

        if (missingResolvers.isNotEmpty()) {
            throw MissingResolverImplementationException(missingResolvers)
        }
    }
}

/**
 * Thrown when a FeatureAppTest schema declares @resolver on fields or types but no corresponding
 * resolver implementation class is found.
 *
 * Each field marked with @resolver in the schema requires a class annotated with @Resolver
 * that extends the generated resolver base class (e.g., QueryResolvers.MyField for field resolvers,
 * NodeResolvers.MyType for node resolvers).
 */
class MissingResolverImplementationException(
    missingResolvers: List<String>
) : RuntimeException(
        buildString {
            append("Missing @Resolver implementation for schema-declared resolvers: ")
            append(missingResolvers.joinToString(", "))
            append(
                ". Each field or type with @resolver in the schema must have a corresponding " +
                    "class annotated with @Resolver that extends the generated resolver base class."
            )
        }
    )
