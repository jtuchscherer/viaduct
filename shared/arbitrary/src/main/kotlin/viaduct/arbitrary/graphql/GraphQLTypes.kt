package viaduct.arbitrary.graphql

import graphql.Directives
import graphql.Scalars
import graphql.introspection.Introspection.DirectiveLocation
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import graphql.schema.GraphQLUnmodifiedType
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.set
import kotlin.math.min
import viaduct.arbitrary.common.CompoundingWeight
import viaduct.arbitrary.common.Config
import viaduct.arbitrary.common.ConfigKey

/** A bag of generated types that can be converted into a GraphQLSchema */
data class GraphQLTypes(
    val interfaces: Map<String, GraphQLInterfaceType>,
    val objects: Map<String, GraphQLObjectType>,
    val inputs: Map<String, GraphQLInputObjectType>,
    val unions: Map<String, GraphQLUnionType>,
    val scalars: Map<String, GraphQLScalarType>,
    val enums: Map<String, GraphQLEnumType>,
    val directives: Map<String, GraphQLDirective>
) {
    val names: Set<String> by lazy {
        val s = mutableSetOf<String>()
        s += interfaces.keys
        s += objects.keys
        s += inputs.keys
        s += unions.keys
        s += scalars.keys
        s += enums.keys
        s += directives.keys
        s.toSet()
    }

    /** Try to resolve a GraphQLTypeReference into a GraphQLType */
    fun resolve(ref: GraphQLTypeReference): GraphQLUnmodifiedType? {
        ref.name.let { name ->
            listOf(interfaces, objects, inputs, unions, scalars, enums).forEach {
                if (it.contains(name)) return it[name]
            }
            return null
        }
    }

    operator fun plus(directive: GraphQLDirective): GraphQLTypes = copy(directives = directives + (directive.name to directive))

    operator fun plus(iface: GraphQLInterfaceType): GraphQLTypes = copy(interfaces = interfaces + (iface.name to iface))

    companion object {
        val empty: GraphQLTypes = GraphQLTypes(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
        )
    }
}

/** Generate arbitrary GraphQLTypes from a static Config */
fun Arb.Companion.graphQLTypes(cfg: Config = Config.default): Arb<GraphQLTypes> =
    Arb
        .graphQLNames(cfg)
        .flatMap { names ->
            graphQLTypes(names, cfg)
        }

/** Generate arbitrary GraphQLTypes from a static Config and static names */
fun Arb.Companion.graphQLTypes(
    names: GraphQLNames,
    cfg: Config = Config.default
): Arb<GraphQLTypes> =
    arbitrary { rs ->
        GraphQLTypesGen(cfg, names, rs).gen()
    }

/** Internal helper class for generating a GraphQLTypes */
internal class GraphQLTypesGen(
    private val cfg: Config,
    private val names: GraphQLNames,
    private val rs: RandomSource
) {
    private fun sampleWeight(key: ConfigKey<Double>): Boolean = rs.sampleWeight(cfg[key])

    @JvmName("sampleCompoundingWeight")
    private fun sampleWeight(key: ConfigKey<CompoundingWeight>): Boolean = rs.sampleWeight(cfg[key].weight)

    fun gen(): GraphQLTypes =
        Arb
            .of(cfg[IncludeTypes])
            .withScalars()
            .withEnums()
            .withInputs()
            .withDirectives()
            .withInterfaces()
            .withObjects()
            .withUnions()
            .finalize()
            .next(rs)

    internal fun genDescription(): String = Arb.graphQLDescription(cfg).next(rs)

    private fun Arb<GraphQLTypes>.withDirectives(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(
                directives = types.directives + genDirectives().associateBy { it.name }
            )
        }

    private fun genDirectives(): List<GraphQLDirective> {
        val validLocations = DirectiveLocation
            .values()
            .toSet()
            .arbSubset(1..DirectiveLocation.values().size)

        return names.directives.map { name ->
            when (val d = builtinDirectives[name]) {
                null -> {
                    GraphQLDirective
                        .newDirective()
                        .name(name)
                        .description(genDescription())
                        .replaceArguments(genArguments(DirectiveHasArgs))
                        .repeatable(sampleWeight(DirectiveIsRepeatable))
                        .also {
                            validLocations.next(rs).forEach(it::validLocation)
                        }.build()
                }
                else -> d
            }
        }
    }

    private fun Arb<GraphQLTypes>.withScalars(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(scalars = types.scalars + genScalars().associateBy { it.name })
        }

    private fun genScalars(): List<GraphQLScalarType> =
        names.scalars.map { name ->
            when (val s = builtinScalars[name]) {
                null ->
                    GraphQLScalarType
                        .newScalar()
                        .name(name)
                        .description(genDescription())
                        .coercing(Scalars.GraphQLString.coercing)
                        .build()
                else -> s
            }
        }

    /**
     * Wrap a provided type in GraphQLList and GraphQLNonNull wrapper types.
     * This function may return a type that has been wrapped multiple times
     */
    internal fun decorate(
        t: GraphQLType,
        allowNonNullable: Boolean = true
    ): GraphQLType {
        tailrec fun wrap(
            type: GraphQLType,
            listBudget: Int
        ): GraphQLType =
            if (type !is GraphQLNonNull && allowNonNullable && sampleWeight(NonNullableness)) {
                wrap(GraphQLNonNull.nonNull(type), listBudget)
            } else if (listBudget > 0 && sampleWeight(Listiness)) {
                wrap(GraphQLList.list(type), listBudget - 1)
            } else {
                type
            }

        return wrap(t, cfg[Listiness].max)
    }

    /**
     * Generate a GraphQLOutputType that is backed by a GraphQLTypeReference and potentially wrapped
     * in GraphQLNonNull and GraphQLList wrappers.
     */
    private fun genOutputTypeRef(): GraphQLOutputType =
        Arb
            .element(names.interfaces + names.scalars + names.unions + names.objects + names.enums)
            .map(GraphQLTypeReference::typeRef)
            .map {
                decorate(it) as GraphQLOutputType
            }.next(rs)

    /**
     * Generate a GraphQLInputType that is backed by a GraphQLTypeReference and potentially wrapped
     * in GraphQLNonNull and GraphQLList wrappers.
     */
    private class InputTypeDescriptor(
        val underlyingTypeName: String,
        val underlyingTypeType: TypeType,
        val type: GraphQLInputType
    ) {
        // This is used to ensure that we do not create cyclic references that graphql-java will reject.
        // This matches GraphQL-Java validation rules, which will reject input
        // cycles that are technically satisfiable even when they contain non-nullable wrappers,
        // such as:
        // input Foo { foos: [[Foo]]! }
        val usedNonNullably: Boolean by lazy {
            tailrec fun loop(t: GraphQLType): Boolean =
                when (t) {
                    is GraphQLNonNull -> true
                    is GraphQLList -> loop(t.originalWrappedType)
                    else -> false
                }
            loop(type)
        }
    }

    private fun genInputTypeDescriptor(allowNonNullable: Boolean = true): Arb<InputTypeDescriptor> {
        val namePools = setOf(TypeType.Scalar, TypeType.Input, TypeType.Enum)
            .mapNotNull { tt ->
                names.names[tt]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { pool -> tt to pool }
            }

        return Arb
            .element(namePools)
            .flatMap { (tt, pool) ->
                Arb.element(pool).map { tt to it }
            }.map { (tt, name) ->
                InputTypeDescriptor(
                    underlyingTypeName = name,
                    underlyingTypeType = tt,
                    type = decorate(GraphQLTypeReference.typeRef(name), allowNonNullable) as GraphQLInputType
                )
            }
    }

    private fun Arb<GraphQLTypes>.withInterfaces(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(interfaces = types.interfaces + genInterfaces())
        }

    private fun genInterfaces(): Map<String, GraphQLInterfaceType> =
        names.interfaces.fold(emptyMap()) { acc, name ->
            val edges = genImplements(acc, InterfaceImplementsInterface)
            val localFields = genFields(InterfaceTypeSize)
            val allFields = (edges.flatMap { it.fields } + localFields)
                .distinctBy { it.name }

            val iface = GraphQLInterfaceType
                .newInterface()
                .name(name)
                .description(genDescription())
                .fields(allFields)
                .replaceInterfaces(edges.toList())
                .build()

            acc + (name to iface)
        }

    private fun genImplements(
        pool: Map<String, GraphQLInterfaceType>,
        weightKey: ConfigKey<CompoundingWeight>,
    ): Set<GraphQLInterfaceType> {
        tailrec fun withImplementedInterfaces(
            acc: Set<GraphQLInterfaceType>,
            unchecked: Set<GraphQLInterfaceType>
        ): Set<GraphQLInterfaceType> =
            if (unchecked.isEmpty()) {
                acc
            } else {
                val iface = unchecked.first()
                val parents = iface.interfaces.map { it as GraphQLInterfaceType }
                val toCheck = parents.filterNot(acc::contains).toSet()
                withImplementedInterfaces(
                    acc = acc + iface,
                    unchecked = (unchecked - iface + toCheck)
                )
            }

        tailrec fun loop(
            edges: Set<GraphQLInterfaceType>,
            edgeBudget: Int,
            pool: Map<String, GraphQLInterfaceType>
        ): Set<GraphQLInterfaceType> =
            if (edgeBudget > 0 && pool.isNotEmpty() && sampleWeight(weightKey)) {
                val newEdge = Arb.of(pool.entries).next(rs).value
                loop(
                    edges = edges + newEdge,
                    edgeBudget = edgeBudget - 1,
                    pool = pool - newEdge.name
                )
            } else {
                edges
            }

        // build base edges
        return loop(emptySet(), cfg[weightKey].max, pool)
            .let { edges ->
                // filter out conflicting edges
                edges.nonConflicting()
            }.let { edges ->
                // then add in all interfaces-of-interfaces
                withImplementedInterfaces(emptySet(), edges)
            }
    }

    private fun genArguments(key: ConfigKey<CompoundingWeight>): List<GraphQLArgument> {
        tailrec fun loop(acc: Map<String, GraphQLArgument>): List<GraphQLArgument> =
            if (acc.size != cfg[key].max && sampleWeight(key)) {
                val itd = genInputTypeDescriptor().next(rs)
                val arg = GraphQLArgument
                    .newArgument()
                    .name(Arb.graphQLArgumentName(cfg).next(rs))
                    .description(genDescription())
                    .type(itd.type)
                    .build()

                loop(acc = acc + (arg.name to arg))
            } else {
                acc.values.toList()
            }

        return loop(emptyMap())
    }

    private fun Arb<GraphQLTypes>.withObjects(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(objects = types.objects + genObjects(types).associateBy { it.name })
        }

    private fun genObjects(types: GraphQLTypes): List<GraphQLObjectType> =
        names.objects.map { name ->
            val implements = genImplements(
                types.interfaces,
                ObjectImplementsInterface
            )
            genObject(name, implements)
        }

    private fun genObject(
        name: String,
        implements: Set<GraphQLInterfaceType>,
    ): GraphQLObjectType {
        val interfaceFields = implements.flatMap { it.fields }
        val allFields = (interfaceFields + genFields(ObjectTypeSize))
            .distinctBy { it.name }

        return GraphQLObjectType
            .newObject()
            .name(name)
            .replaceInterfaces(implements.toList())
            .description(genDescription())
            .fields(allFields)
            .build()
    }

    private fun genFields(sizeKey: ConfigKey<IntRange>): List<GraphQLFieldDefinition> =
        Arb
            .int(cfg[sizeKey])
            .map { size ->
                List(size) { genField() }.distinctBy { it.name }
            }.next(rs)

    private fun genField(): GraphQLFieldDefinition =
        GraphQLFieldDefinition
            .newFieldDefinition()
            .name(Arb.graphQLFieldName(cfg).next(rs))
            .description(genDescription())
            .arguments(genArguments(FieldHasArgs))
            .type(genOutputTypeRef())
            .build()

    private fun Arb<GraphQLTypes>.withInputs(): Arb<GraphQLTypes> =
        map { types ->
            // OneOf types can be arranged to form types for which values cannot be created.
            // For example:
            //   input A @oneOf { b:B }
            //   input B @oneOf { a:A }
            // In order to not generate values in this style, it helps to know all the OneOf's
            // that we're going to generate ahead of time.
            val oneOfTypes = names.inputs.filter { sampleWeight(OneOfWeight) }
                .toSet()

            /**
             * while input _type_ generation needs to only know the names of other input objects,
             * input _field_ generation may try to generate default values that require knowing
             * not just the names of other inputs, but also their fields.
             *
             * Break with the pattern used elsewhere in this class and fold over input type generation,
             * allowing later input types to generate values that refer to previously generated input types.
             *
             * Names are sorted to put them into lexicographic order, it's expected that any generated
             * edges that refer to types earlier-in-the-list may be non-nullable. This assumption is also
             * baked into [isUnsatisfiableInputEdge]
             */

            names.inputs.sorted().fold(types) { acc, name ->
                val isOneOf = name in oneOfTypes
                val fields = genInputFields(name, isOneOf, InputObjectTypeSize)

                val inp = GraphQLInputObjectType
                    .newInputObject()
                    .name(name)
                    .description(genDescription())
                    .fields(fields)
                    .apply {
                        if (isOneOf) {
                            withDirective(Directives.OneOfDirective)

                            // If we detect that we've generated an uninhabited oneof, insert a field that allows
                            // value creation
                            if (isUninhabitedOneOf(name, fields, oneOfTypes)) {
                                val reliefFieldName = Arb.graphQLFieldName(cfg).filterNot { it == name }.next(rs)
                                field(
                                    GraphQLInputObjectField.newInputObjectField()
                                        .name(reliefFieldName)
                                        .type(Scalars.GraphQLInt)
                                        .build()
                                )
                            }
                        }
                    }
                    .build()

                acc.copy(inputs = acc.inputs + (name to inp))
            }
        }

    private fun genInputFields(
        hostType: String,
        isOneOf: Boolean,
        key: ConfigKey<IntRange>
    ): List<GraphQLInputObjectField> {
        val arbInputField = genInputTypeDescriptor(allowNonNullable = !isOneOf)
            .filterNot { isUnsatisfiableInputEdge(hostType, it) }
            .zip(Arb.graphQLFieldName(cfg))
            .map { (itd, fn) ->
                GraphQLInputObjectField
                    .newInputObjectField()
                    .name(fn)
                    .description(genDescription())
                    .type(itd.type)
                    .build()
            }

        return Arb
            .set(arbInputField, cfg[key])
            .map { it.distinctBy { it.name } }
            .next(rs)
    }

    /**
     * An unsatisfiable input edge is an edge between input types for which it can be impossible to generate a value.
     * This is a problem that is specific to non-nullable input object types, which can create cyclic relationships
     * that require an infinitely large value to satisfy.
     *
     * A simple example of this relationship is:
     *   input A { b: B! }
     *   input B { a: A! }
     *
     * In this example, creating a value for A would require an infinitely nested object, {b: {a: {b: {...}}}
     * This is described in more detail at https://spec.graphql.org/October2021/#sec-Input-Objects.Circular-References
     *
     * Since it is difficult to know during type generation if an input field is going to be part of a cycle,
     * this method uses a heuristic that direct non-nullable edges are only allowed between two objects if the
     * host type name is lexicographically greater than the field's type name.
     *
     * An example of an allowed schema:
     *   input A { b: B }    // host type A < field type B    ->  edge is unsatisfiable
     *   input B { a: A! }   // host type B > field type A    ->  edge is satisfiable
     *
     * This allows generating circular references that can be satisfied with finite values.
     */
    private fun isUnsatisfiableInputEdge(
        hostTypeName: String,
        inputTypeDescriptor: InputTypeDescriptor
    ): Boolean =
        inputTypeDescriptor.underlyingTypeName.compareTo(hostTypeName) != -1 &&
            inputTypeDescriptor.usedNonNullably &&
            inputTypeDescriptor.underlyingTypeType == TypeType.Input

    /**
     * OneOfs can form "uninhabited" subgraphs, where there are no possible values that can be constructed
     * for the type.
     * An example of this is:
     *     input A @oneOf { a:A }
     *
     * These kinds of types are considered valid according to the spec, but they create problems by making types
     * inaccessible to GraphQL values.
     *
     * A fix to make these types invalid is proposed here:
     *   https://github.com/graphql/graphql-spec/pull/1211
     *
     * This method applies a heuristic that will allow us to create interesting cyclic OneOf types while
     * rejecting the fewest possible type graphs.
     *
     * Following the convention of [isUnsatisfiableInputEdge], the rules are:
     *   1. for an input object to be inhabited, it must define at least one inhabited field
     *   2. if a field's type includes a List wrapper, or its unwrapped type is not a OneOf, assume that the
     *      field's type is inhabited
     *   2. if a field's type name is a oneOf and is lexicographically greater than hostTypeName,
     *      assume that the field's type is inhabited
     *   3. if a field's type name is a oneOf and is lexicographically less-than or equal-to the
     *      hostTypeName, assume that this field is uninhabited
     *
     * Examples:
     * An uninhabited graph:
     *   input A @oneOf { a:A }   // host type A == field type A  -> edge may be uninhabited
     *
     * An inhabited one:
     *   input A @oneOf { b:B }   // host type A < field type B -> edge may be uninhabited
     *   input B @oneOf { a:A }   // host type B < field type A -> assume that A is inhabited
     */
    private fun isUninhabitedOneOf(
        hostTypeName: String,
        fields: List<GraphQLInputObjectField>,
        oneOfTypes: Set<String>
    ): Boolean =
        fields.all { f ->
            assert(f.type !is GraphQLNonNull)
            if (f.type is GraphQLList) return@all false
            val inner = GraphQLTypeUtil.unwrapAllAs<GraphQLNamedType>(f.type).name
            if (inner !in oneOfTypes) return@all false
            // if hostTypeName is smaller-than ("A" < "B") or equal to ("A" == "A") than the field's
            // OneOf type, then assume that this field is uninhabited.
            hostTypeName.compareTo(inner) < 1
        }

    private fun Arb<GraphQLTypes>.withUnions(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(unions = types.unions + genUnions().associateBy { it.name })
        }

    private fun Arb<GraphQLTypes>.finalize(): Arb<GraphQLTypes> =
        if (cfg[GenInterfaceStubsIfNeeded]) {
            map(::addMissingImpls)
        } else {
            this
        }

    private fun addMissingImpls(types: GraphQLTypes): GraphQLTypes {
        tailrec fun loop(
            ifaces: Set<String>,
            objs: List<GraphQLObjectType>
        ): Set<String> =
            if (ifaces.isEmpty() || objs.isEmpty()) {
                ifaces
            } else {
                loop(
                    ifaces - objs
                        .first()
                        .interfaces
                        .map { it.name }
                        .toSet(),
                    objs.drop(1)
                )
            }

        val unimplemented = loop(types.interfaces.keys, types.objects.values.toList())
        val newObjects = unimplemented.map { iname ->
            val ifaces = types.interfaces[iname]!!.let { iface ->
                val parents = iface.interfaces.map { types.interfaces[it.name]!! }
                parents.toSet() + iface
            }
            genObject(iname + "_STUB", ifaces)
        }
        return types.copy(
            objects = types.objects + newObjects.associateBy { it.name }
        )
    }

    private fun genUnions(): List<GraphQLUnionType> {
        if (names.unions.isEmpty() || names.objects.isEmpty()) {
            return emptyList()
        }

        // Arb.set can be finicky with what it calls slippage, which is when it wants to
        // generate a set of a certain size but has to make multiple attempts due to the
        // underlying generator returning the same element multiple times.
        // For unions, it's not critically important that we get the exact right size every
        // time, so just use a list generator and remove duplicates
        val arbMembers = Arb
            .list(
                gen = Arb.element(names.objects),
                range = IntRange(
                    min(names.objects.size, cfg[UnionTypeSize].first),
                    min(names.objects.size, cfg[UnionTypeSize].last)
                )
            ).map { it.distinct() }

        return names.unions.map { name ->
            val members = arbMembers.next(rs).map(::GraphQLTypeReference)

            GraphQLUnionType
                .newUnionType()
                .name(name)
                .description(genDescription())
                .replacePossibleTypes(members)
                .build()
        }
    }

    private fun genEnumValues(): List<GraphQLEnumValueDefinition> =
        // Generating enum values using Arb.set can throw an IllegalStateException if it can't
        // generate the target size. This becomes more likely as the target size grows and collisions
        // become harder to avoid.
        // Use Arb.list instead which is close enough and more reliable
        Arb
            .list(
                Arb.graphQLEnumValueName(cfg),
                cfg[EnumTypeSize]
            ).map { values ->
                values.toSet().map {
                    GraphQLEnumValueDefinition
                        .newEnumValueDefinition()
                        .name(it)
                        .build()
                }
            }.next(rs)

    private fun Arb<GraphQLTypes>.withEnums(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(enums = types.enums + genEnums().associateBy { it.name })
        }

    private fun genEnums(): List<GraphQLEnumType> =
        names.enums.map { name ->
            GraphQLEnumType
                .newEnum()
                .name(name)
                .description(genDescription())
                .values(genEnumValues())
                .build()
        }
}
