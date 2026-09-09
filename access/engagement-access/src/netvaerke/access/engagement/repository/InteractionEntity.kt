package netvaerke.access.engagement.repository

import java.time.Instant
import kotlin.uuid.Uuid
import netvaerke.access.engagement.InteractionChannel

data class InteractionEntity(
    val id: Uuid,
    val tenantId: Uuid,
    val resourceId: Uuid,
    val userId: Uuid,
    val channel: InteractionChannel,
    val notes: String?,
    val occurredAt: Instant,
    val createdAt: Instant? = null,
)
