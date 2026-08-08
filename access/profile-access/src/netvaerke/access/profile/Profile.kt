package netvaerke.access.profile

import kotlin.uuid.Uuid

data class Profile(
    val userId: Uuid,
    val name: String,
    val email: String,
)
