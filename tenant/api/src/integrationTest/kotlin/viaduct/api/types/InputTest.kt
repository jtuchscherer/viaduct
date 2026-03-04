package viaduct.api.types

import graphql.schema.GraphQLEnumType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import viaduct.api.internal.InputTypeFactory
import viaduct.api.schemautils.SchemaUtils
import viaduct.apiannotations.InternalApi

@OptIn(InternalApi::class)
class InputTest {
    @Test
    fun testInputType() {
        val inputType = InputTypeFactory.inputObjectInputType("Input1", SchemaUtils.getSchema())
        assertTrue(inputType.getField("enumFieldWithDefault").type is GraphQLEnumType)
    }
}
