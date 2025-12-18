package viaduct.api.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import viaduct.api.globalid.GlobalIDImpl
import viaduct.api.mocks.MockGlobalID
import viaduct.api.mocks.MockGlobalIDCodec
import viaduct.api.mocks.MockReflectionLoader
import viaduct.api.mocks.MockType
import viaduct.api.types.NodeObject
import viaduct.api.types.Object

class GlobalIDCodecTest {
    @Test
    fun `serialize delegates to service codec`() {
        val type = MockType.mkNodeObject("TestType")
        val globalId = MockGlobalID(type, "internal-123")
        val reflectionLoader = MockReflectionLoader(type)

        val codec = GlobalIDCodec(MockGlobalIDCodec, reflectionLoader)
        val serialized = codec.serialize(globalId)

        assertEquals("TestType:internal-123", serialized)
    }

    @Test
    fun `deserialize reconstructs GlobalID with proper type`() {
        val type = MockType.mkNodeObject("TestType")
        val reflectionLoader = MockReflectionLoader(type)

        val codec = GlobalIDCodec(MockGlobalIDCodec, reflectionLoader)
        val deserialized = codec.deserialize<NodeObject>("TestType:internal-123")

        assertEquals("TestType", deserialized.type.name)
        assertEquals("internal-123", deserialized.internalID)
    }

    @Test
    fun `serialize and deserialize roundtrip`() {
        val type = MockType.mkNodeObject("User")
        val reflectionLoader = MockReflectionLoader(type)
        val codec = GlobalIDCodec(MockGlobalIDCodec, reflectionLoader)

        val originalId = GlobalIDImpl(type, "user-456")
        val serialized = codec.serialize(originalId)
        val deserialized = codec.deserialize<NodeObject>(serialized)

        assertEquals(originalId.type.name, deserialized.type.name)
        assertEquals(originalId.internalID, deserialized.internalID)
    }

    @Test
    fun `deserialize throws for non-NodeObject type`() {
        val nonNodeType = MockType("NonNodeType", Object::class)
        val reflectionLoader = MockReflectionLoader(nonNodeType)

        val codec = GlobalIDCodec(MockGlobalIDCodec, reflectionLoader)

        val exception = assertThrows<IllegalArgumentException> {
            codec.deserialize<NodeObject>("NonNodeType:id-123")
        }
        assertEquals("type `NonNodeType` from GlobalID 'NonNodeType:id-123' is not a NodeObject", exception.message)
    }

    @Test
    fun `deserialize with different internal IDs`() {
        val type = MockType.mkNodeObject("Product")
        val reflectionLoader = MockReflectionLoader(type)
        val codec = GlobalIDCodec(MockGlobalIDCodec, reflectionLoader)

        val id1 = codec.deserialize<NodeObject>("Product:abc")
        val id2 = codec.deserialize<NodeObject>("Product:xyz")

        assertEquals("abc", id1.internalID)
        assertEquals("xyz", id2.internalID)
        assertEquals(id1.type.name, id2.type.name)
    }
}
