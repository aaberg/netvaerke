package netvaerke.engine.authorization

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AuthorizationResponseDto(
    val authorized: Boolean
)

@Serializable
enum class Operation {
    ReadContacts,
    UpdateContacts,
}