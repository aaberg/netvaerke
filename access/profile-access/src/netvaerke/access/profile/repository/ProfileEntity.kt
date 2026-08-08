package netvaerke.access.profile.repository

import java.time.Instant
import kotlin.uuid.Uuid

data class ProfileEntity(
    val userId: Uuid,
    val name: String,
    val email: String,
    val createdAt: Instant? = null,
)
