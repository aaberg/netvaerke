package netvaerke.access.engagement

import java.time.Instant
import kotlin.uuid.Uuid

data class Interaction(
    val id: Uuid,
    val contactId: Uuid,
    val userId: Uuid,
    val channel: InteractionChannel,
    val notes: String?,
    val occurredAt: Instant,
    val createdAt: Instant? = null,
)

enum class InteractionChannel {
    PHONE,
    EMAIL,
    TEXT,
    CHAT,
    IN_PERSON,
}
