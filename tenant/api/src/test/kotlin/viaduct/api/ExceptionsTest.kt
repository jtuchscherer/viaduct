@file:Suppress("ForbiddenImport")

package viaduct.api

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExceptionsTest {
    @Test
    fun `test wrapFrameworkErrors with ViaductTenantException`() {
        val exception = ViaductTenantUsageException("Tenant error")
        val thrown = assertThrows(ViaductTenantUsageException::class.java) {
            wrapFrameworkErrors("Test message") {
                throw exception
            }
        }
        assertEquals(exception, thrown)
    }

    @Test
    fun `test wrapFrameworkErrors with other exception`() {
        val exception = RuntimeException("Runtime error")
        val thrown = assertThrows(ViaductFrameworkException::class.java) {
            wrapFrameworkErrors("Test message") {
                throw exception
            }
        }
        assertEquals("Test message (java.lang.RuntimeException: Runtime error)", thrown.message)
        assertEquals(exception, thrown.cause)
    }

    @Test
    fun `test wrapFrameworkErrorsSuspend with ViaductTenantException`() {
        val exception = ViaductTenantUsageException("Tenant error")
        val thrown = assertThrows(ViaductTenantUsageException::class.java) {
            runBlocking {
                wrapFrameworkErrorsSuspend("Test message") {
                    throw exception
                }
            }
        }
        assertEquals(exception, thrown)
    }

    @Test
    fun `test wrapFrameworkErrorsSuspend with other exception`() {
        val exception = RuntimeException("Runtime error")
        val thrown = assertThrows(ViaductFrameworkException::class.java) {
            runBlocking {
                wrapFrameworkErrorsSuspend("Test message") {
                    throw exception
                }
            }
        }
        assertEquals("Test message (java.lang.RuntimeException: Runtime error)", thrown.message)
        assertEquals(exception, thrown.cause)
    }
}
