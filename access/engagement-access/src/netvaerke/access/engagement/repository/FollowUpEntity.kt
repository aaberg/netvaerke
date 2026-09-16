package netvaerke.access.engagement.repository

import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid
import netvaerke.access.engagement.FollowUpCadence
import netvaerke.access.engagement.FollowUpStatus

internal data class FollowUpEntity(
    val id: Uuid,
    val resourceId: Uuid,
    val ruleId: Uuid?,
    val dueOn: LocalDate,
    val dueDateRevision: Long,
    val cadence: FollowUpCadence?,
    val status: FollowUpStatus,
    val completedOn: LocalDate?,
    val createdAt: Instant,
)

internal data class FollowUpRuleEntity(
    val cadence: FollowUpCadence,
    val lastCompletedOn: LocalDate?,
)
