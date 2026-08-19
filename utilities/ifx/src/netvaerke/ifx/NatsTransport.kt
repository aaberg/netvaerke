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
import java.time.Duration as JavaDuration
import java.util.concurrent.CompletionException
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.jvm.kotlinFunction
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
                argumentSerializers = method.argumentTypes().map { serializer(it).asAnySerializer() },
                responseSerializer = serializer(method.returnKType()).asAnySerializer(),
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
        val sourceArguments = arguments.dropLast(1)
        suspend { invokeRemote(codec, sourceArguments) }.startCoroutine(continuation)
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
                val request = json.decodeFromString(NatsRequest.serializer(), message.data.decodeToString())
                Result.success(invokeService(codec, target, codec.decodeArguments(json, request)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
            publishReply(replyTo, codec, result)
        }
    }

    private suspend fun invokeRemote(codec: MethodCodec, arguments: List<Any?>): Any? {
        require(arguments.size == codec.argumentSerializers.size) {
            "${service.typeName}.${codec.method.name} received ${arguments.size} arguments, " +
                "expected ${codec.argumentSerializers.size}"
        }
        val request = NatsRequest(
            arguments = arguments.mapIndexed { index, argument ->
                json.encodeToJsonElement(codec.argumentSerializers[index], argument)
            },
        )
        val payload = json.encodeToString(NatsRequest.serializer(), request).encodeToByteArray()
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

    private suspend fun invokeService(codec: MethodCodec, target: Any, arguments: List<Any?>): Any? =
        suspendCancellableCoroutine { continuation ->
            try {
                val invocationArguments = arguments.toMutableList().apply { add(continuation) }
                val returned = codec.method.invoke(target, *invocationArguments.toTypedArray())
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
    val argumentSerializers: List<KSerializer<Any?>>,
    val responseSerializer: KSerializer<Any?>,
) {
    fun decodeArguments(json: Json, request: NatsRequest): List<Any?> {
        require(request.arguments.size == argumentSerializers.size) {
            "${method.declaringClass.typeName}.${method.name} received ${request.arguments.size} arguments, " +
                "expected ${argumentSerializers.size}"
        }
        return request.arguments.mapIndexed { index, argument ->
            json.decodeFromJsonElement(argumentSerializers[index], argument)
        }
    }
}

@Serializable
private data class NatsRequest(
    val arguments: List<JsonElement>,
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

private fun Method.argumentTypes(): List<KType> = kotlinFunctionOrThrow().parameters
    .filter { it.kind == KParameter.Kind.VALUE }
    .map(KParameter::type)

private fun Method.returnKType(): KType = kotlinFunctionOrThrow().returnType

private fun Method.kotlinFunctionOrThrow() = requireNotNull(kotlinFunction) {
    "$declaringClass.$name must be a Kotlin function to use NATS transport"
}.also { function ->
    require(function.parameters.none { it.kind == KParameter.Kind.EXTENSION_RECEIVER }) {
        "$declaringClass.$name cannot be an extension function"
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
