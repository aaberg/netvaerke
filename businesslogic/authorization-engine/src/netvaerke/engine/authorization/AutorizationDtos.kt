package netvaerke.engine.authorization

import kotlinx.serialization.Serializable

@Serializable
data class AuthorizationResponseDto(
    val authorized: Boolean
)

@Serializable
enum class Operation {
    READ_CONTACTS,
    UPDATE_CONTACTS,
}