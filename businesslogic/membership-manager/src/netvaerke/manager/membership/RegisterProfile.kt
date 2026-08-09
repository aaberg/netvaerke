package netvaerke.manager.membership

import kotlin.uuid.Uuid

data class RegisterProfileRequest (
    val userId: Uuid,
    val name: String,
    val email: String
)

