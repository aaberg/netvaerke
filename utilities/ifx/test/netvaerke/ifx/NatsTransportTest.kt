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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
    fun `serializes zero and multiple arguments through NATS`() {
        val subject = natsSubject()
        val recorded = AtomicReference<String>()
        NatsTestBroker.openConnection().use { connection ->
            Ifx {
                service<NatsArgumentService> {
                    via(NatsTransport(connection).requestReply(subject))
                }
            }.use { ifx ->
                ifx.expose<NatsArgumentService>(object : NatsArgumentService {
                    override suspend fun defaultGreeting(): NatsGreetingResponse = NatsGreetingResponse("Hello")

                    override suspend fun greet(
                        greeting: String,
                        name: String,
                        suffix: String?,
                    ): NatsGreetingResponse = NatsGreetingResponse("$greeting, $name${suffix.orEmpty()}")

                    override suspend fun record(name: String, count: Int) {
                        recorded.set("$name:$count")
                    }
                })
                ifx.start()

                val service = ifx.create<NatsArgumentService>()
                assertEquals(NatsGreetingResponse("Hello"), runNatsSuspend { service.defaultGreeting() })
                assertEquals(
                    NatsGreetingResponse("Hello, Lars!"),
                    runNatsSuspend { service.greet("Hello", "Lars", "!") },
                )
                assertEquals(
                    NatsGreetingResponse("Hello, Lars"),
                    runNatsSuspend { service.greet("Hello", "Lars", null) },
                )
                runNatsSuspend { service.record("Lars", 2) }
                assertEquals("Lars:2", recorded.get())
            }
        }
    }

    @Test
    fun `rejects malformed argument envelopes`() {
        val subject = natsSubject()
        NatsTestBroker.openConnection().use { connection ->
            Ifx {
                service<NatsGreetingService> {
                    via(NatsTransport(connection).requestReply(subject))
                }
            }.use { ifx ->
                ifx.expose<NatsGreetingService>(NatsGreetingServiceImpl())
                ifx.start()

                val missingArgumentReply = connection.request(
                    "$subject.greet",
                    """{"arguments":[]}""".encodeToByteArray(),
                ).get(10, TimeUnit.SECONDS)
                assertTrue(missingArgumentReply.data.decodeToString().contains("expected 1"))

                val invalidArgumentReply = connection.request(
                    "$subject.greet",
                    """{"arguments":["not a greeting"]}""".encodeToByteArray(),
                ).get(10, TimeUnit.SECONDS)
                assertTrue(invalidArgumentReply.data.decodeToString().contains("error"))
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
                        yield()
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
                            yield()
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

    @Test
    fun `cancels running service handlers when IFX closes`() = runBlocking {
        val subject = natsSubject()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        NatsTestBroker.openConnection().use { connection ->
            val ifx = Ifx {
                service<NatsGreetingService> {
                    via(NatsTransport(connection).requestReply(subject))
                }
            }
            ifx.expose<NatsGreetingService>(object : NatsGreetingService {
                override suspend fun greet(request: NatsGreetingRequest): NatsGreetingResponse {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            })
            ifx.start()
            val request = async {
                ifx.create<NatsGreetingService>().greet(NatsGreetingRequest("Lars"))
            }

            try {
                started.await()
                ifx.close()
                cancelled.await()
            } finally {
                ifx.close()
                request.cancelAndJoin()
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

private interface NatsArgumentService {
    suspend fun defaultGreeting(): NatsGreetingResponse
    suspend fun greet(greeting: String, name: String, suffix: String?): NatsGreetingResponse
    suspend fun record(name: String, count: Int)
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
