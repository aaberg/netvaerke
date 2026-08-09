package netvaerke.ifx

import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.Message
import io.nats.client.impl.Headers
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.extension.kotlin.asContextElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.time.Duration as JavaDuration
import java.util.concurrent.CompletionException
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

public class NatsTransport(
    private val connection: Connection,
    private val json: Json = Json,
    private val requestTimeout: Duration = 5.seconds,
    private val requestHandlerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    init {
        require(requestTimeout.isPositive()) { "NATS request timeout must be positive" }
    }

    public fun requestReply(
        subject: String,
        queueGroup: String = subject,
    ): IfxTransport {
        requireValidNatsSubject(subject)
        require(queueGroup.isNotBlank() && queueGroup.none(Char::isWhitespace)) {
            "NATS queue group cannot be blank or contain whitespace"
        }
        return object : IfxTransport {
            override fun bind(service: Class<*>): IfxServiceBinding = bind(service, null)

            override fun bind(service: Class<*>, openTelemetry: OpenTelemetry?): IfxServiceBinding =
                NatsRequestReplyBinding(
                    service = service,
                    connection = connection,
                    json = json,
                    requestTimeout = requestTimeout,
                    subject = subject,
                    queueGroup = queueGroup,
                    propagator = openTelemetry?.propagators?.textMapPropagator,
                    requestHandlerDispatcher = requestHandlerDispatcher,
                )
        }
    }
}

public class IfxRemoteException(
    public val remoteType: String,
    message: String?,
) : RuntimeException("Remote $remoteType: ${message ?: "No message"}")

@OptIn(ExperimentalSerializationApi::class)
private class NatsRequestReplyBinding(
    private val service: Class<*>,
    private val connection: Connection,
    private val json: Json,
    private val requestTimeout: Duration,
    subject: String,
    private val queueGroup: String,
    private val propagator: TextMapPropagator?,
    requestHandlerDispatcher: CoroutineDispatcher,
) : IfxServiceBinding {
    private val codecs = service.methods
        .filterNot { it.declaringClass == Any::class.java }
        .associate { method ->
            require(method.name.none(Char::isWhitespace) && method.name.none { it == '.' || it == '*' || it == '>' }) {
                "${service.typeName}.${method.name} cannot be used as a NATS operation name"
            }
            val operationSubject = "$subject.${method.name}"
            method to MethodCodec(
                method = method,
                subject = operationSubject,
                requestSerializer = serializer(method.genericParameterTypes.first()).asAnySerializer(),
                responseSerializer = serializer(method.responseType()).asAnySerializer(),
            )
        }
    private val codecsBySubject = codecs.values.associateBy(MethodCodec::subject)
    private val requestScope = CoroutineScope(SupervisorJob() + requestHandlerDispatcher)
    private var implementation: Any? = null
    private var dispatcher: Dispatcher? = null

    override fun expose(implementation: Any) {
        require(this.implementation == null) { "${service.typeName} is already exposed" }
        require(service.isInstance(implementation)) {
            "${implementation::class.java.typeName} does not implement ${service.typeName}"
        }
        this.implementation = implementation
    }

    override fun invoke(method: Method, arguments: Array<out Any?>): Any? {
        val codec = checkNotNull(codecs[method]) {
            "${service.typeName}.${method.name} is not a configured operation"
        }
        val continuation = arguments.last().asContinuation()
        val requestObject = arguments.first()
        suspend { invokeRemote(codec, requestObject) }.startCoroutine(continuation)
        return COROUTINE_SUSPENDED
    }

    override fun start() {
        if (implementation == null || dispatcher != null) return

        val created = connection.createDispatcher(::handleRequest)
        try {
            codecs.values.forEach { codec ->
                created.subscribe(codec.subject, queueGroup)
            }
            connection.flush(requestTimeout.toJavaDuration())
            dispatcher = created
        } catch (failure: Throwable) {
            connection.closeDispatcher(created)
            throw failure
        }
    }

    override fun close() {
        dispatcher?.let(connection::closeDispatcher)
        dispatcher = null
        requestScope.cancel()
    }

    private fun handleRequest(message: Message) {
        val codec = codecsBySubject[message.subject] ?: return
        val target = implementation ?: return
        val replyTo = message.replyTo ?: return

        requestScope.launch(extractContext(message).asContextElement()) {
            val result = try {
                val request = json.decodeFromString(codec.requestSerializer, message.data.decodeToString())
                Result.success(invokeService(codec, target, request))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
            publishReply(replyTo, codec, result)
        }
    }

    private suspend fun invokeRemote(codec: MethodCodec, requestObject: Any?): Any? {
        val payload = json.encodeToString(codec.requestSerializer, requestObject).encodeToByteArray()
        return suspendCancellableCoroutine { continuation ->
            val pendingRequest = request(codec.subject, payload)
            continuation.invokeOnCancellation { pendingRequest.cancel(true) }
            pendingRequest.whenComplete { message, failure ->
                val result = if (failure == null) {
                    runCatching { decodeReply(codec, checkNotNull(message)) }
                } else {
                    Result.failure(failure.unwrapCompletionException())
                }
                continuation.resumeWith(result)
            }
        }
    }

    private suspend fun invokeService(codec: MethodCodec, target: Any, request: Any?): Any? =
        suspendCancellableCoroutine { continuation ->
            try {
                val returned = codec.method.invoke(target, request, continuation)
                if (returned !== COROUTINE_SUSPENDED) {
                    continuation.resume(returned)
                }
            } catch (exception: java.lang.reflect.InvocationTargetException) {
                continuation.resumeWithException(exception.targetException)
            } catch (failure: Throwable) {
                continuation.resumeWithException(failure)
            }
        }

    private fun request(subject: String, payload: ByteArray) =
        propagator?.let { propagator ->
            val headers = Headers()
            propagator.inject(Context.current(), headers, NatsHeaderSetter)
            connection.requestWithTimeout(subject, headers, payload, requestTimeout.toJavaDuration())
        } ?: connection.requestWithTimeout(subject, payload, requestTimeout.toJavaDuration())

    private fun extractContext(message: Message): Context =
        if (propagator == null || !message.hasHeaders()) {
            Context.root()
        } else {
            propagator.extract(Context.root(), message.headers, NatsHeaderGetter)
        }

    private fun publishReply(replyTo: String, codec: MethodCodec, result: Result<Any?>) {
        val reply = result.fold(
            onSuccess = { value ->
                NatsReply(value = json.encodeToJsonElement(codec.responseSerializer, value))
            },
            onFailure = { failure ->
                NatsReply(
                    error = NatsError(
                        type = failure::class.qualifiedName ?: failure::class.java.typeName,
                        message = failure.message,
                    ),
                )
            },
        )
        connection.publish(replyTo, json.encodeToString(NatsReply.serializer(), reply).encodeToByteArray())
    }

    private fun decodeReply(codec: MethodCodec, message: Message): Any? {
        val reply = json.decodeFromString(NatsReply.serializer(), message.data.decodeToString())
        reply.error?.let { throw IfxRemoteException(it.type, it.message) }
        return json.decodeFromJsonElement(
            codec.responseSerializer,
            requireNotNull(reply.value) { "NATS reply did not contain a value" },
        )
    }
}

private data class MethodCodec(
    val method: Method,
    val subject: String,
    val requestSerializer: KSerializer<Any?>,
    val responseSerializer: KSerializer<Any?>,
)

@Serializable
private data class NatsReply(
    val value: JsonElement? = null,
    val error: NatsError? = null,
)

@Serializable
private data class NatsError(
    val type: String,
    val message: String?,
)

private fun Method.responseType(): Type {
    val continuationType = genericParameterTypes.last() as? ParameterizedType
        ?: error("$name has no parameterized continuation")
    val resultType = continuationType.actualTypeArguments.single()
    return if (resultType is WildcardType) {
        resultType.lowerBounds.singleOrNull() ?: resultType.upperBounds.single()
    } else {
        resultType
    }
}

@Suppress("UNCHECKED_CAST")
private fun KSerializer<*>.asAnySerializer(): KSerializer<Any?> = this as KSerializer<Any?>

@Suppress("UNCHECKED_CAST")
private fun Any?.asContinuation(): Continuation<Any?> =
    this as? Continuation<Any?> ?: error("Suspend invocation has no continuation")

private fun Throwable.unwrapCompletionException(): Throwable =
    if (this is CompletionException && cause != null) cause!! else this

private fun Duration.toJavaDuration(): JavaDuration =
    JavaDuration.ofSeconds(inWholeSeconds, inWholeNanoseconds % 1_000_000_000)

private fun requireValidNatsSubject(subject: String) {
    require(
        subject.isNotBlank() &&
            subject.none(Char::isWhitespace) &&
            subject.split('.').all(String::isNotEmpty) &&
            '*' !in subject &&
            '>' !in subject,
    ) {
        "NATS service subject must contain non-empty tokens without whitespace or wildcards"
    }
}

private object NatsHeaderSetter : TextMapSetter<Headers> {
    override fun set(carrier: Headers?, key: String, value: String) {
        carrier?.put(key, value)
    }
}

private object NatsHeaderGetter : TextMapGetter<Headers> {
    override fun keys(carrier: Headers): Iterable<String> = carrier.keySet()

    override fun get(carrier: Headers?, key: String): String? =
        carrier?.getIgnoreCase(key)?.firstOrNull()
}
