package netvaerke.ifx

import java.lang.reflect.Method

/** A transport configured for one service interface. */
public fun interface IfxTransport {
    public fun bind(service: Class<*>): IfxServiceBinding
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
