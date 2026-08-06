package netvaerke.ifx

import io.opentelemetry.api.OpenTelemetry
import java.lang.reflect.Method

/** A transport configured for one service interface. */
public fun interface IfxTransport {
    public fun bind(service: Class<*>): IfxServiceBinding

    /**
     * Binds a service with IFX's optional tracing configuration.
     *
     * Transports that do not need access to tracing configuration can rely on this default
     * implementation. Transports that carry requests across process boundaries can use the
     * supplied instance to propagate the active context.
     */
    public fun bind(service: Class<*>, openTelemetry: OpenTelemetry?): IfxServiceBinding = bind(service)
}

/** Runtime binding used by IFX to invoke and expose one service. */
public interface IfxServiceBinding : AutoCloseable {
    public fun invoke(method: Method, arguments: Array<out Any?>): Any?

    public fun expose(implementation: Any) {
        throw UnsupportedOperationException("This transport cannot expose services")
    }

    public fun start() = Unit

    override fun close() = Unit
}
