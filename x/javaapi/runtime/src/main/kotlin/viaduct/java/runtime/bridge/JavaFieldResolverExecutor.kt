package viaduct.java.runtime.bridge

import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.future.await
import viaduct.engine.api.EngineExecutionContext
import viaduct.engine.api.EngineObjectData
import viaduct.engine.api.EngineSelectionSet
import viaduct.engine.api.FieldResolverExecutor
import viaduct.engine.api.RequiredSelectionSet
import viaduct.engine.api.ResolverMetadata
import viaduct.java.api.context.FieldExecutionContext

/**
 * Kotlin bridge that wraps a Java resolver and implements [FieldResolverExecutor]
 * for the Viaduct engine.
 *
 * This bridge converts between:
 * - Java CompletableFuture ↔ Kotlin suspend functions
 * - Java FieldExecutionContext ↔ Kotlin EngineExecutionContext
 *
 * @param resolveFunction A function that takes a FieldExecutionContext and returns a CompletableFuture
 * @param resolverId Unique identifier for this resolver (e.g., "Query.greeting")
 * @param resolverName Human-readable resolver name for metadata
 */
class JavaFieldResolverExecutor(
    private val resolveFunction: (FieldExecutionContext<*, *, *, *>) -> CompletableFuture<*>,
    override val resolverId: String,
    private val resolverName: String,
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
            resolveOne(
                arguments = selector.arguments,
                objectValue = selector.objectValue,
                queryValue = selector.queryValue,
                selections = selector.selections,
                context = context
            )
        }

        return mapOf(selector to result)
    }

    private suspend fun resolveOne(
        arguments: Map<String, Any?>,
        objectValue: EngineObjectData,
        queryValue: EngineObjectData,
        selections: EngineSelectionSet?,
        context: EngineExecutionContext,
    ): Any? {
        // Create a minimal Java context adapter
        val javaContext = SimpleFieldExecutionContext(
            arguments = arguments,
            objectValue = objectValue,
            queryValue = queryValue,
            selections = selections,
            requestContext = context.requestContext
        )

        // Call the Java resolver function and await the CompletableFuture
        val future = resolveFunction(javaContext)
        return future.await()
    }
}
