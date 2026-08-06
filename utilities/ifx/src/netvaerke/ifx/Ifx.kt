package netvaerke.ifx

import io.opentelemetry.api.OpenTelemetry
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

public class Ifx internal constructor(
    private val bindings: Map<Class<*>, IfxServiceBinding>,
    private val tracing: IfxTracing?,
) : AutoCloseable {
    private var state = State.CONFIGURED

    public inline fun <reified T : Any> create(): T = create(T::class.java)

    public fun <T : Any> create(service: Class<T>): T {
        check(state != State.CLOSED) { "IFX is closed" }
        val binding = bindings[service]
            ?: throw IllegalArgumentException("${service.typeName} is not configured")
        val handler = InvocationHandler { proxy, method, arguments ->
            if (method.declaringClass == Any::class.java) {
                invokeObjectMethod(proxy, method, arguments)
            } else {
                val invocationArguments = arguments ?: emptyArray()
                if (tracing == null) {
                    binding.invoke(method, invocationArguments)
                } else {
                    tracing.invokeClient(service, method, invocationArguments) {
                        binding.invoke(method, it)
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(service.classLoader, arrayOf(service), handler) as T
    }

    public inline fun <reified T : Any> expose(implementation: T) {
        expose(T::class.java, implementation)
    }

    public fun <T : Any> expose(service: Class<T>, implementation: T) {
        check(state == State.CONFIGURED) { "Services can only be exposed before IFX is started" }
        val binding = bindings[service]
            ?: throw IllegalArgumentException("${service.typeName} is not configured")
        require(service.isInstance(implementation)) {
            "${implementation::class.java.typeName} does not implement ${service.typeName}"
        }
        binding.expose(tracing?.wrapServer(service, implementation) ?: implementation)
    }

    public fun start(): Ifx {
        check(state != State.CLOSED) { "IFX is closed" }
        if (state == State.STARTED) return this

        bindings.values.forEach(IfxServiceBinding::start)
        state = State.STARTED
        return this
    }

    override fun close() {
        if (state == State.CLOSED) return

        bindings.values.toList().asReversed().forEach(IfxServiceBinding::close)
        state = State.CLOSED
    }

    private fun invokeObjectMethod(proxy: Any, method: Method, arguments: Array<out Any?>?): Any =
        when (method.name) {
            "equals" -> proxy === arguments?.singleOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Ifx proxy for ${proxy.javaClass.interfaces.single().typeName}"
            else -> error("Unsupported Object method: ${method.name}")
        }

    private enum class State {
        CONFIGURED,
        STARTED,
        CLOSED,
    }

    public companion object {
        public operator fun invoke(configuration: IfxBuilder.() -> Unit): Ifx =
            IfxBuilder().apply(configuration).build()
    }
}

public class IfxBuilder {
    private val services = linkedMapOf<Class<*>, IfxTransport>()
    private var openTelemetry: OpenTelemetry? = null

    /** Enables OpenTelemetry tracing for all services configured in this IFX instance. */
    public fun tracing(openTelemetry: OpenTelemetry) {
        require(this.openTelemetry == null) { "OpenTelemetry tracing is already configured" }
        this.openTelemetry = openTelemetry
    }

    public inline fun <reified T : Any> service(noinline configuration: IfxServiceConfiguration.() -> Unit) {
        service(T::class.java, configuration)
    }

    public fun <T : Any> service(
        service: Class<T>,
        configuration: IfxServiceConfiguration.() -> Unit,
    ) {
        require(service !in services) { "${service.typeName} is already configured" }
        ServiceContract.validate(service)
        val transport = IfxServiceConfiguration().apply(configuration).transport()
        services[service] = transport
    }

    internal fun build(): Ifx {
        val bindings = services.mapValues { (service, transport) ->
            transport.bind(service, openTelemetry)
        }
        return Ifx(
            bindings = bindings,
            tracing = openTelemetry?.let(::IfxTracing),
        )
    }
}

public class IfxServiceConfiguration internal constructor() {
    private var transport: IfxTransport? = null

    public fun via(transport: IfxTransport) {
        require(this.transport == null) { "A service can only use one transport" }
        this.transport = transport
    }

    internal fun transport(): IfxTransport =
        requireNotNull(transport) { "A service transport must be configured with via(...)" }
}
