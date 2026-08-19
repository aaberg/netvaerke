package netvaerke.ifx

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IfxTest {
    @Test
    fun `creates a direct proxy`() {
        val ifx = Ifx {
            service<GreetingService> {
                via(DirectTransport)
            }
        }
        ifx.expose<GreetingService>(GreetingServiceImpl())

        val greeting = runSuspend { ifx.create<GreetingService>().greet(GreetingRequest("Lars")) }

        assertEquals(GreetingResponse("Hello, Lars"), greeting)
    }

    @Test
    fun `propagates direct exceptions unchanged`() {
        val expected = GreetingException()
        val ifx = Ifx {
            service<GreetingService> {
                via(DirectTransport)
            }
        }
        ifx.expose<GreetingService>(object : GreetingService {
            override suspend fun greet(request: GreetingRequest): GreetingResponse = throw expected
        })

        val actual = assertFailsWith<GreetingException> {
            runSuspend { ifx.create<GreetingService>().greet(GreetingRequest("Lars")) }
        }

        assertSame(expected, actual)
    }

    @Test
    fun `implements object methods without invoking the service`() {
        val ifx = Ifx {
            service<GreetingService> {
                via(DirectTransport)
            }
        }
        val first = ifx.create<GreetingService>()
        val second = ifx.create<GreetingService>()

        assertNotEquals(first, second)
        assertEquals(System.identityHashCode(first), first.hashCode())
        assertEquals("Ifx proxy for netvaerke.ifx.GreetingService", first.toString())
    }

    @Test
    fun `rejects non-suspend service methods`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            Ifx {
                service<InvalidService> {
                    via(DirectTransport)
                }
            }
        }

        assertTrue(failure.message.orEmpty().contains("must be a suspend function"))
    }

    @Test
    fun `supports zero and multiple request arguments`() {
        val ifx = Ifx {
            service<NoRequestService> {
                via(DirectTransport)
            }
            service<MultipleArgumentService> {
                via(DirectTransport)
            }
        }
        ifx.expose<NoRequestService>(object : NoRequestService {
            override suspend fun greet(): GreetingResponse = GreetingResponse("Hello")
        })
        ifx.expose<MultipleArgumentService>(object : MultipleArgumentService {
            override suspend fun greet(greeting: String, name: String, suffix: String?): GreetingResponse =
                GreetingResponse("$greeting, $name${suffix.orEmpty()}")
        })

        assertEquals(GreetingResponse("Hello"), runSuspend { ifx.create<NoRequestService>().greet() })
        assertEquals(
            GreetingResponse("Hello, Lars!"),
            runSuspend { ifx.create<MultipleArgumentService>().greet("Hello", "Lars", "!") },
        )
        assertEquals(
            GreetingResponse("Hello, Lars"),
            runSuspend { ifx.create<MultipleArgumentService>().greet("Hello", "Lars", null) },
        )
    }

}

private data class GreetingRequest(val name: String)

private data class GreetingResponse(val message: String)

private interface GreetingService {
    suspend fun greet(request: GreetingRequest): GreetingResponse
}

private class GreetingServiceImpl : GreetingService {
    override suspend fun greet(request: GreetingRequest): GreetingResponse =
        GreetingResponse("Hello, ${request.name}")
}

private interface InvalidService {
    fun greet(request: GreetingRequest): GreetingResponse
}

private interface NoRequestService {
    suspend fun greet(): GreetingResponse
}

private interface MultipleArgumentService {
    suspend fun greet(greeting: String, name: String, suffix: String?): GreetingResponse
}

private class GreetingException : RuntimeException()

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return checkNotNull(outcome) { "Test coroutine did not complete synchronously" }.getOrThrow()
}
