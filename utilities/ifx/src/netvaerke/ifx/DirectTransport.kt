package netvaerke.ifx

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

public object DirectTransport : IfxTransport {
    override fun bind(service: Class<*>): IfxServiceBinding = DirectBinding(service)
}

private class DirectBinding(
    private val service: Class<*>,
) : IfxServiceBinding {
    private var implementation: Any? = null

    override fun expose(implementation: Any) {
        require(this.implementation == null) { "${service.typeName} is already exposed" }
        require(service.isInstance(implementation)) {
            "${implementation::class.java.typeName} does not implement ${service.typeName}"
        }
        this.implementation = implementation
    }

    override fun invoke(method: Method, arguments: Array<out Any?>): Any? {
        val target = checkNotNull(implementation) {
            "No direct implementation is exposed for ${service.typeName}"
        }
        return try {
            method.invoke(target, *arguments)
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }
}
