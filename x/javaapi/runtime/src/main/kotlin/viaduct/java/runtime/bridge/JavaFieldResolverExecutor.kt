package viaduct.java.runtime.bridge

import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.future.await
import viaduct.engine.api.EngineExecutionContext
import viaduct.engine.api.FieldResolverExecutor
import viaduct.engine.api.RequiredSelectionSet
import viaduct.engine.api.ResolverMetadata
import viaduct.java.api.context.FieldExecutionContext
import viaduct.java.api.types.Arguments

/**
 * Kotlin bridge that wraps a Java resolver and implements [FieldResolverExecutor]
 * for the Viaduct engine.
 *
 * This bridge converts between:
 * - Java CompletableFuture <-> Kotlin suspend functions
 * - Java FieldExecutionContext <-> Kotlin EngineExecutionContext
 * - Engine argument maps <-> Typed Java Arguments instances
 *
 * @param resolveFunction A function that takes a FieldExecutionContext and returns a CompletableFuture
 * @param resolverId Unique identifier for this resolver (e.g., "Query.greeting")
 * @param resolverName Human-readable resolver name for metadata
 * @param argumentsClass The Java Arguments class for this resolver, used to create typed instances
 *        from the engine's argument map. Null if the resolver takes no arguments.
 */
class JavaFieldResolverExecutor(
    private val resolveFunction: (FieldExecutionContext<*, *, *, *>) -> CompletableFuture<*>,
    override val resolverId: String,
    private val resolverName: String,
    private val argumentsClass: Class<out Arguments>? = null,
) : FieldResolverExecutor {
    override val objectSelectionSet: RequiredSelectionSet? = null
    override val querySelectionSet: RequiredSelectionSet? = null
    override val metadata: ResolverMetadata = ResolverMetadata.forModern(resolverName)
    override val isBatching: Boolean = false

    override suspend fun batchResolve(
        selectors: List<FieldResolverExecutor.Selector>,
        context: EngineExecutionContext
    ): Map<FieldResolverExecutor.Selector, Result<Any?>> {
        // Unbatched resolver only handles single selector
        require(selectors.size == 1) {
            "Unbatched Java resolver should only receive single selector, got ${selectors.size}"
        }

        val selector = selectors.first()
        val result = runCatching {
            resolveOne(selector = selector, context = context)
        }

        return mapOf(selector to result)
    }

    private suspend fun resolveOne(
        selector: FieldResolverExecutor.Selector,
        context: EngineExecutionContext,
    ): Any? {
        val arguments = createArguments(selector.arguments)

        val javaContext = SimpleFieldExecutionContext(
            requestContext = context.requestContext,
            arguments = arguments,
        )

        // Call the Java resolver function and await the CompletableFuture
        val future = resolveFunction(javaContext)
        return future.await()
    }

    /**
     * Creates a typed Arguments instance from the engine's argument map using reflection.
     *
     * For each entry in the map, finds a matching setter on the Arguments class and invokes it.
     * Nested maps are recursively converted to the setter's expected parameter type.
     * Returns null if no arguments class is configured (resolver takes no arguments).
     */
    private fun createArguments(argumentMap: Map<String, Any?>): Arguments? {
        if (argumentsClass == null || argumentsClass == Arguments.None::class.java) {
            return null
        }

        return populateFromMap(argumentsClass, argumentMap) as Arguments
    }

    /**
     * Creates an instance of the given class and populates it from a map using setter reflection.
     *
     * Handles nested maps by recursively creating instances of the setter's parameter type.
     */
    @Suppress("UNCHECKED_CAST")
    private fun populateFromMap(
        clazz: Class<*>,
        map: Map<String, Any?>
    ): Any {
        val instance = clazz.getDeclaredConstructor().newInstance()
        for ((key, value) in map) {
            val setterName = "set${key.replaceFirstChar { it.uppercase() }}"
            val setter = clazz.methods.firstOrNull { it.name == setterName && it.parameterCount == 1 }
                ?: continue
            val paramType = setter.parameterTypes[0]
            val convertedValue = when {
                value == null -> null
                value is Map<*, *> && !Map::class.java.isAssignableFrom(paramType) ->
                    populateFromMap(paramType, value as Map<String, Any?>)
                else -> value
            }
            setter.invoke(instance, convertedValue)
        }
        return instance
    }
}
