package netvaerke.ifx

import java.lang.reflect.Method
import kotlin.coroutines.Continuation

internal object ServiceContract {
    fun validate(service: Class<*>) {
        require(service.isInterface) {
            "${service.typeName} must be an interface"
        }

        val methods = service.methods.filterNot { it.declaringClass == Any::class.java }
        require(methods.isNotEmpty()) {
            "${service.typeName} must declare at least one service method"
        }

        val overloadedMethod = methods.groupBy(Method::getName).entries.firstOrNull { it.value.size > 1 }
        require(overloadedMethod == null) {
            "${service.typeName} cannot overload service method '${overloadedMethod?.key}'"
        }

        methods.forEach { method -> validateMethod(service, method) }
    }

    private fun validateMethod(service: Class<*>, method: Method) {
        require(!method.isDefault) {
            "${service.typeName}.${method.name} cannot have a default implementation"
        }
        require(method.typeParameters.isEmpty()) {
            "${service.typeName}.${method.name} cannot declare type parameters"
        }
        require(
            method.returnType == Any::class.java &&
                method.parameterTypes.lastOrNull() == Continuation::class.java,
        ) {
            "${service.typeName}.${method.name} must be a suspend function"
        }
        require(method.parameterCount == 2) {
            "${service.typeName}.${method.name} must take exactly one request object"
        }
    }
}
