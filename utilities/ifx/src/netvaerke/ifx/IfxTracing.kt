package netvaerke.ifx

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal class IfxTracing(
    openTelemetry: OpenTelemetry,
) {
    private val tracer = openTelemetry.getTracer("netvaerke.ifx")

    fun invokeClient(
        service: Class<*>,
        method: Method,
        arguments: Array<out Any?>,
        invoke: (Array<out Any?>) -> Any?,
    ): Any? = invokeTraced(SpanKind.CLIENT, service, method, arguments, invoke)

    fun wrapServer(service: Class<*>, implementation: Any): Any {
        val handler = InvocationHandler { proxy, method, arguments ->
            if (method.declaringClass == Any::class.java) {
                invokeObjectMethod(proxy, method, arguments)
            } else {
                invokeTraced(SpanKind.SERVER, service, method, arguments ?: emptyArray()) { invocationArguments ->
                    try {
                        method.invoke(implementation, *invocationArguments)
                    } catch (exception: InvocationTargetException) {
                        throw exception.targetException
                    }
                }
            }
        }
        return Proxy.newProxyInstance(service.classLoader, arrayOf(service), handler)
    }

    private fun invokeTraced(
        kind: SpanKind,
        service: Class<*>,
        method: Method,
        arguments: Array<out Any?>,
        invoke: (Array<out Any?>) -> Any?,
    ): Any? {
        val span = tracer.spanBuilder("ifx.${kind.name.lowercase()} ${service.name}.${method.name}")
            .setSpanKind(kind)
            .setAttribute("rpc.service", service.name)
            .setAttribute("rpc.method", method.name)
            .startSpan()
        val originalContinuation = arguments.lastOrNull().asContinuation()
        val tracedContinuation = object : Continuation<Any?> {
            override val context = originalContinuation.context

            override fun resumeWith(result: Result<Any?>) {
                finish(span, result.exceptionOrNull())
                originalContinuation.resumeWith(result)
            }
        }
        val invocationArguments = Array<Any?>(arguments.size) { index ->
            if (index == arguments.lastIndex) tracedContinuation else arguments[index]
        }
        val scope = span.makeCurrent()
        try {
            val result = invoke(invocationArguments)
            if (result !== COROUTINE_SUSPENDED) {
                finish(span, null)
            }
            return result
        } catch (failure: Throwable) {
            finish(span, failure)
            throw failure
        } finally {
            scope.close()
        }
    }

    private fun finish(span: Span, failure: Throwable?) {
        if (failure != null) {
            span.recordException(failure)
            span.setStatus(StatusCode.ERROR)
        }
        span.end()
    }

    private fun invokeObjectMethod(proxy: Any, method: Method, arguments: Array<out Any?>?): Any =
        when (method.name) {
            "equals" -> proxy === arguments?.singleOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Ifx tracing proxy for ${proxy.javaClass.interfaces.single().typeName}"
            else -> error("Unsupported Object method: ${method.name}")
        }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asContinuation(): Continuation<Any?> =
    this as? Continuation<Any?> ?: error("Suspend invocation has no continuation")
