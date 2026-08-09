package netvaerke.access.profile

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val userId: Uuid,
    val name: String,
    val email: String,
)
