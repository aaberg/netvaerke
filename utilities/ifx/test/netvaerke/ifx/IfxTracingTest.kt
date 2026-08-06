package netvaerke.ifx

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IfxTracingTest {
    @Test
    fun `traces direct client and server calls`() {
        val tracing = TestTracing.create()
        try {
            val ifx = Ifx {
                tracing(tracing.openTelemetry)
                service<TracedGreetingService> {
                    via(DirectTransport)
                }
            }
            ifx.expose<TracedGreetingService>(TracedGreetingServiceImpl())

            assertEquals(TracedGreetingResponse("Hello, Lars"), runTracedSuspend {
                ifx.create<TracedGreetingService>().greet(TracedGreetingRequest("Lars"))
            })

            val spans = tracing.exporter.finishedSpanItems.associateBy { it.name }
            val client = checkNotNull(spans["ifx.client netvaerke.ifx.TracedGreetingService.greet"])
            val server = checkNotNull(spans["ifx.server netvaerke.ifx.TracedGreetingService.greet"])

            assertEquals(client.spanContext.traceId, server.spanContext.traceId)
            assertEquals(client.spanContext.spanId, server.parentSpanContext.spanId)
            assertEquals("netvaerke.ifx.TracedGreetingService", client.attributes.get(AttributeService))
            assertEquals("greet", client.attributes.get(AttributeMethod))
            assertEquals("netvaerke.ifx.TracedGreetingService", server.attributes.get(AttributeService))
            assertEquals("greet", server.attributes.get(AttributeMethod))
        } finally {
            tracing.close()
        }
    }

    @Test
    fun `marks client and server spans as errors`() {
        val tracing = TestTracing.create()
        try {
            val ifx = Ifx {
                tracing(tracing.openTelemetry)
                service<TracedGreetingService> {
                    via(DirectTransport)
                }
            }
            ifx.expose<TracedGreetingService>(object : TracedGreetingService {
                override suspend fun greet(request: TracedGreetingRequest): TracedGreetingResponse =
                    throw TracedGreetingFailure()
            })

            val failure = runCatching {
                runTracedSuspend { ifx.create<TracedGreetingService>().greet(TracedGreetingRequest("Lars")) }
            }.exceptionOrNull()

            assertTrue(failure is TracedGreetingFailure)
            assertTrue(tracing.exporter.finishedSpanItems.all { it.status.statusCode.name == "ERROR" })
        } finally {
            tracing.close()
        }
    }

    @Test
    fun `ends spans when a direct service completes asynchronously`() {
        val tracing = TestTracing.create()
        try {
            val ifx = Ifx {
                tracing(tracing.openTelemetry)
                service<TracedGreetingService> {
                    via(DirectTransport)
                }
            }
            ifx.expose<TracedGreetingService>(object : TracedGreetingService {
                override suspend fun greet(request: TracedGreetingRequest): TracedGreetingResponse =
                    suspendCoroutine { continuation ->
                        Thread {
                            continuation.resume(TracedGreetingResponse("Hello, ${request.name}"))
                        }.start()
                    }
            })

            assertEquals(TracedGreetingResponse("Hello, Lars"), runTracedSuspend {
                ifx.create<TracedGreetingService>().greet(TracedGreetingRequest("Lars"))
            })
            assertEquals(2, tracing.exporter.finishedSpanItems.size)
        } finally {
            tracing.close()
        }
    }
}

private val AttributeService = io.opentelemetry.api.common.AttributeKey.stringKey("rpc.service")
private val AttributeMethod = io.opentelemetry.api.common.AttributeKey.stringKey("rpc.method")

private data class TracedGreetingRequest(val name: String)

private data class TracedGreetingResponse(val message: String)

private interface TracedGreetingService {
    suspend fun greet(request: TracedGreetingRequest): TracedGreetingResponse
}

private class TracedGreetingServiceImpl : TracedGreetingService {
    override suspend fun greet(request: TracedGreetingRequest): TracedGreetingResponse =
        TracedGreetingResponse("Hello, ${request.name}")
}

private class TracedGreetingFailure : RuntimeException()

internal class TestTracing private constructor(
    val exporter: InMemorySpanExporter,
    private val tracerProvider: SdkTracerProvider,
    val openTelemetry: OpenTelemetry,
) : AutoCloseable {
    override fun close() {
        tracerProvider.close()
    }

    companion object {
        fun create(): TestTracing {
            val exporter = InMemorySpanExporter.create()
            val tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build()
            return TestTracing(
                exporter = exporter,
                tracerProvider = tracerProvider,
                openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build(),
            )
        }
    }
}

private fun <T> runTracedSuspend(block: suspend () -> T): T {
    val outcome = CompletableFuture<Result<T>>()
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            outcome.complete(result)
        }
    })
    return outcome.get().getOrThrow()
}
