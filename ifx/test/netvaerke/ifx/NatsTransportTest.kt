package netvaerke.ifx

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import netvaerke.testsupport.NatsTestBroker

class NatsTransportTest {
    @Test
    fun `serializes request and response through NATS`() {
        val subject = natsSubject()
        NatsTestBroker.openConnection().use { connection ->
            Ifx {
                service<NatsGreetingService> {
                    via(NatsTransport(connection).requestReply(subject))
                }
            }.use { ifx ->
                ifx.expose<NatsGreetingService>(NatsGreetingServiceImpl())
                ifx.start()

                val response = runNatsSuspend {
                    ifx.create<NatsGreetingService>().greet(NatsGreetingRequest("Lars"))
                }

                assertEquals(NatsGreetingResponse("Hello, Lars"), response)
            }
        }
    }

    @Test
    fun `maps service failures to remote exceptions`() {
        val subject = natsSubject()
        NatsTestBroker.openConnection().use { connection ->
            Ifx {
                service<NatsGreetingService> {
                    via(NatsTransport(connection).requestReply(subject))
                }
            }.use { ifx ->
                ifx.expose<NatsGreetingService>(object : NatsGreetingService {
                    override suspend fun greet(request: NatsGreetingRequest): NatsGreetingResponse {
                        throw IllegalStateException("Greeting failed")
                    }
                })
                ifx.start()

                val failure = assertFailsWith<IfxRemoteException> {
                    runNatsSuspend {
                        ifx.create<NatsGreetingService>().greet(NatsGreetingRequest("Lars"))
                    }
                }

                assertEquals("java.lang.IllegalStateException", failure.remoteType)
                assertTrue(failure.message.orEmpty().contains("Greeting failed"))
            }
        }
    }
}

private fun natsSubject(): String = "greetings.${UUID.randomUUID()}"

@Serializable
private data class NatsGreetingRequest(val name: String)

@Serializable
private data class NatsGreetingResponse(val message: String)

private interface NatsGreetingService {
    suspend fun greet(request: NatsGreetingRequest): NatsGreetingResponse
}

private class NatsGreetingServiceImpl : NatsGreetingService {
    override suspend fun greet(request: NatsGreetingRequest): NatsGreetingResponse =
        NatsGreetingResponse("Hello, ${request.name}")
}

private fun <T> runNatsSuspend(block: suspend () -> T): T {
    val outcome = CompletableFuture<Result<T>>()
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            outcome.complete(result)
        }
    })
    return outcome.get(10, TimeUnit.SECONDS).getOrThrow()
}
