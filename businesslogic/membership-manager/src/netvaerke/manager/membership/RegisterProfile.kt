package netvaerke.manager.membership

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
data class RegisterProfileRequest(
    val userId: Uuid,
    val name: String,
    val email: String,
)
