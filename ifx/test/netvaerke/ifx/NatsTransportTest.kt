package netvaerke.ifx

import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.Message
import io.nats.client.MessageHandler
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

class NatsTransportTest {
    @Test
    fun `serializes request and response through NATS`() {
        val broker = FakeNatsConnection()
        val nats = NatsTransport(broker.connection)
        val ifx = Ifx {
            service<NatsGreetingService> {
                via(nats.requestReply(subject = "greetings.v1"))
            }
        }
        ifx.expose<NatsGreetingService>(NatsGreetingServiceImpl())
        ifx.start()

        val response = runNatsSuspend {
            ifx.create<NatsGreetingService>().greet(NatsGreetingRequest("Lars"))
        }

        assertEquals(NatsGreetingResponse("Hello, Lars"), response)
        assertEquals(setOf("greetings.v1.greet"), broker.subscriptions)
    }

    @Test
    fun `maps service failures to remote exceptions`() {
        val broker = FakeNatsConnection()
        val nats = NatsTransport(broker.connection)
        val ifx = Ifx {
            service<NatsGreetingService> {
                via(nats.requestReply(subject = "greetings.v1"))
            }
        }
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

private class FakeNatsConnection {
    val subscriptions = mutableSetOf<String>()
    val connection: Connection = javaProxy(::handleConnectionCall)

    private val inboxCounter = AtomicInteger()
    private val pendingRequests = mutableMapOf<String, CompletableFuture<Message>>()
    private var messageHandler: MessageHandler? = null
    private val dispatcher: Dispatcher = javaProxy(::handleDispatcherCall)

    private fun handleConnectionCall(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? =
        when (method.name) {
            "createDispatcher" -> {
                messageHandler = arguments?.first() as MessageHandler
                dispatcher
            }
            "requestWithTimeout" -> {
                val subject = arguments?.get(0) as String
                val data = arguments[1] as ByteArray
                val replyTo = "_INBOX.${inboxCounter.incrementAndGet()}"
                val future = CompletableFuture<Message>()
                pendingRequests[replyTo] = future
                checkNotNull(messageHandler) { "No NATS dispatcher is registered" }
                    .onMessage(message(subject, replyTo, data))
                future
            }
            "publish" -> {
                val subject = arguments?.get(0) as String
                val data = arguments[1] as ByteArray
                checkNotNull(pendingRequests.remove(subject)) {
                    "No request is waiting for $subject"
                }.complete(message(subject, null, data))
                null
            }
            "closeDispatcher" -> null
            else -> objectMethodOrDefault(proxy, method, arguments)
        }

    private fun handleDispatcherCall(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? =
        when (method.name) {
            "subscribe" -> {
                subscriptions += arguments?.first() as String
                proxy
            }
            "unsubscribe" -> proxy
            else -> objectMethodOrDefault(proxy, method, arguments)
        }

    private fun message(subject: String, replyTo: String?, data: ByteArray): Message =
        javaProxy { proxy, method, arguments ->
            when (method.name) {
                "getSubject" -> subject
                "getReplyTo" -> replyTo
                "getData" -> data
                else -> objectMethodOrDefault(proxy, method, arguments)
            }
        }
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified T : Any> javaProxy(
    crossinline handler: (Any, Method, Array<out Any?>?) -> Any?,
): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
    InvocationHandler { proxy, method, arguments -> handler(proxy, method, arguments) },
) as T

private fun objectMethodOrDefault(
    proxy: Any,
    method: Method,
    arguments: Array<out Any?>?,
): Any? = when (method.name) {
    "equals" -> proxy === arguments?.singleOrNull()
    "hashCode" -> System.identityHashCode(proxy)
    "toString" -> "Fake ${proxy.javaClass.interfaces.single().simpleName}"
    else -> when (method.returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0F
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> 0.toChar()
        else -> null
    }
}

private fun <T> runNatsSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return checkNotNull(outcome) { "Test coroutine did not complete" }.getOrThrow()
}
