@file:Suppress("ForbiddenImport")

package viaduct.arbitrary.graphql

import graphql.introspection.Introspection
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.InputValueWithState
import graphql.schema.SchemaTraverser
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import io.kotest.property.Arb
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.checkAll
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import viaduct.arbitrary.common.CompoundingWeight
import viaduct.arbitrary.common.Config
import viaduct.arbitrary.common.KotestPropertyBase

class GraphQLSchemasTest : KotestPropertyBase() {
    // It's useful to have a directive that can be applied to any element
    private val testDirective = GraphQLDirective
        .newDirective()
        .name("testDirective")
        .repeatable(false)
        .validLocations(*Introspection.DirectiveLocation.values())
        .build()

    /**
     * This test makes no assertions but is useful for debugging
     * schema generation.
     * Uncomment the @Test annotation to run, but please don't check in.
     */
    // @Test()
    fun `dump 1 schema`(): Unit =
        runBlocking {
            val cfg = Config.default + (DescriptionLength to 0..0)
            Arb.graphQLSchema(cfg).checkAll(1) {
                val sdl = SchemaPrinter().print(it)
                println(sdl)
                markSuccess()
            }
        }

    @Test
    fun `Arb-graphQLSchema can generate an empty-ish schema`(): Unit =
        runBlocking {
            val cfg = Config.default + (SchemaSize to 0)
            Arb.graphQLSchema(cfg).checkAll {
                markSuccess()
            }
        }

    @Test
    fun `Arb-graphQLSchema can generate a schema from an empty GraphQLTypes`(): Unit =
        runBlocking {
            Arb.graphQLSchema(GraphQLTypes.empty).forAll {
                true
            }
        }

    @Test
    fun `Arb-graphQLSchema can generate a schema from arbitrary GraphQLTypes`(): Unit =
        runBlocking {
            val cfg = Config.default + (SchemaSize to 10)
            Arb.graphQLTypes(cfg).flatMap { types ->
                Arb.graphQLSchema(types, cfg)
            }.forAll {
                true
            }
        }

    @Test
    fun `Arb-viaductSchema can generate schemass`(): Unit =
        runBlocking {
            Arb.viaductSchema().checkAll {
                markSuccess()
            }
        }

    @Test
    fun `schema document can be roundtripped through sdl`(): Unit =
        runBlocking {
            Arb.graphQLSchema().forAll(100) { schema ->
                val sdl = SchemaPrinter().print(schema)
                SchemaParser().parse(sdl)
                true
            }
        }

    @Test
    fun `adds default values`(): Unit =
        runBlocking {
            // disabled
            Arb.graphQLSchema(Config.default + (DefaultValueWeight to 0.0))
                .forAll(100) { schema ->
                    val defaultables = CollectDefaultables(includeOneOfFields = true)
                        .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                        .defaultables

                    defaultables.none { it.isSet }
                }

            // enabled
            Arb.graphQLSchema(Config.default + (DefaultValueWeight to 1.0))
                .forAll(100) { schema ->
                    val defaultables = CollectDefaultables(includeOneOfFields = false)
                        .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                        .defaultables
                    defaultables.all { it.isSet }
                }
        }

    @Test
    fun `adds applied directives`(): Unit =
        runBlocking {
            val cfg = Config.default + (IncludeTypes to (GraphQLTypes.empty + testDirective))

            // disabled
            Arb.graphQLSchema(cfg + (AppliedDirectiveWeight to CompoundingWeight.Never))
                .forAll(100) { schema ->
                    val dirs = CollectAppliedDirectives()
                        .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                        .directives
                    dirs.isEmpty()
                }

            // enabled
            Arb.graphQLSchema(cfg + (AppliedDirectiveWeight to CompoundingWeight.Once))
                .forAll(100) { schema ->
                    val dirs = CollectAppliedDirectives()
                        .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                        .directives
                    dirs.isNotEmpty()
                }
        }

    @Test
    fun `adds applied directives -- but not on introspection elements`(): Unit =
        runBlocking {
            val cfg = Config.default +
                (AppliedDirectiveWeight to CompoundingWeight.Always) +
                (DirectiveIsRepeatable to 0.0) +
                (IncludeTypes to (GraphQLTypes.empty + testDirective))

            Arb.graphQLSchema(cfg)
                .forAll(100) { schema ->
                    val dirs = CollectAppliedDirectives()
                        .also { visitor ->
                            val introspectionRoots = listOf(
                                schema.introspectionSchemaType,
                                schema.introspectionSchemaFieldDefinition,
                                schema.introspectionTypenameFieldDefinition,
                                schema.introspectionTypeFieldDefinition,
                            )
                            introspectionRoots.forEach { elt ->
                                SchemaTraverser().depthFirst(visitor, elt)
                            }
                        }
                        .directives
                    dirs.isEmpty()
                }
        }

    @Test
    fun `generates inhabited OneOf types`(): Unit =
        runBlocking {
            // an inhabited type is a type for which it is possible to create a finite value
            // https://en.wikipedia.org/wiki/Type_inhabitation
            fun isInhabited(type: GraphQLInputObjectType): Boolean {
                fun loop(
                    seenOneOfs: Set<String>,
                    type: GraphQLInputType
                ): Boolean {
                    assert(type !is GraphQLNonNull)
                    return if (type is GraphQLInputObjectType && type.isOneOf) {
                        val checkableFields = type.fields.filter { f ->
                            if (f.type is GraphQLList) return@filter true
                            val unwrappedFieldType = GraphQLTypeUtil.unwrapAll(f.type)
                            unwrappedFieldType !is GraphQLInputObjectType || unwrappedFieldType.name !in seenOneOfs
                        }
                        checkableFields.any { f ->
                            loop(seenOneOfs + type.name, f.type)
                        }
                    } else {
                        true
                    }
                }

                return loop(emptySet(), type)
            }

            // create a configuration that is likely to produce closed OneOf subgraphs
            // this is characterized by a high generation of OneOf types with small numbers of fields
            val cfg = Config.default +
                (InputObjectTypeSize to 1.asIntRange()) +
                (TypeTypeWeights to TypeTypeWeights.zero + (TypeType.Input to 1.0)) +
                (ExplicitNullValueWeight to 0.0) +
                (OneOfWeight to 1.0)

            Arb.graphQLSchema(cfg)
                .forAll { schema ->
                    schema.allTypesAsList
                        .mapNotNull { it as? GraphQLInputObjectType }
                        .all(::isInhabited)
                }
        }

    private class CollectAppliedDirectives : GraphQLTypeVisitorStub() {
        var directives = mutableListOf<GraphQLAppliedDirective>()

        override fun visitGraphQLAppliedDirective(
            node: GraphQLAppliedDirective,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            directives += node
            return TraversalControl.CONTINUE
        }
    }

    private class CollectDefaultables(val includeOneOfFields: Boolean) : GraphQLTypeVisitorStub() {
        var defaultables = mutableListOf<InputValueWithState>()

        override fun visitGraphQLArgument(
            node: GraphQLArgument,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            defaultables += node.argumentDefaultValue
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLInputObjectField(
            node: GraphQLInputObjectField,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            val parent = context.parentNode
            if (parent is GraphQLInputObjectType && parent.isOneOf && !includeOneOfFields) {
                return TraversalControl.CONTINUE
            }
            defaultables += node.inputFieldDefaultValue
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLDirective(
            node: GraphQLDirective,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            // skip built-in directives, some of which define arguments with default values that are outside the control of this generator
            if (node.name in builtinDirectives) {
                return TraversalControl.ABORT
            }
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLType(
            node: GraphQLSchemaElement,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl =
            // skip introspection fields and types, which are outside the control of this generator
            if (node is GraphQLNamedType && node.name.startsWith("__")) {
                TraversalControl.ABORT
            } else {
                TraversalControl.CONTINUE
            }
    }
}
