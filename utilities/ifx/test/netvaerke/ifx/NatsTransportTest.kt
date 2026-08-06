package netvaerke.ifx

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
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

    @Test
    fun `propagates trace context through NATS headers`() {
        val tracing = TestTracing.create()
        try {
            val subject = natsSubject()
            val serverSpan = AtomicReference<SpanContext>()
            NatsTestBroker.openConnection().use { connection ->
                Ifx {
                    tracing(tracing.openTelemetry)
                    service<NatsGreetingService> {
                        via(NatsTransport(connection).requestReply(subject))
                    }
                }.use { ifx ->
                    ifx.expose<NatsGreetingService>(object : NatsGreetingService {
                        override suspend fun greet(request: NatsGreetingRequest): NatsGreetingResponse {
                            serverSpan.set(Span.current().spanContext)
                            return NatsGreetingResponse("Hello, ${request.name}")
                        }
                    })
                    ifx.start()

                    runNatsSuspend {
                        ifx.create<NatsGreetingService>().greet(NatsGreetingRequest("Lars"))
                    }
                }
            }

            val spans = tracing.exporter.finishedSpanItems.associateBy { it.name }
            val client = checkNotNull(spans["ifx.client netvaerke.ifx.NatsGreetingService.greet"])
            val server = checkNotNull(spans["ifx.server netvaerke.ifx.NatsGreetingService.greet"])

            assertEquals(client.spanContext.traceId, server.spanContext.traceId)
            assertEquals(client.spanContext.spanId, server.parentSpanContext.spanId)
            assertEquals(server.spanContext, serverSpan.get())
        } finally {
            tracing.close()
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
