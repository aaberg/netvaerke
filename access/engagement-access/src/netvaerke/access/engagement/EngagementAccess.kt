package netvaerke.access.engagement

import java.time.LocalDate
import kotlin.uuid.Uuid

interface EngagementAccess {
    suspend fun registerInteraction(tenantId: Uuid, interaction: Interaction): Boolean
    suspend fun updateInteraction(tenantId: Uuid, interaction: Interaction): Boolean
    suspend fun deleteInteraction(tenantId: Uuid, interactionId: Uuid): Boolean
    suspend fun getInteraction(tenantId: Uuid, interactionId: Uuid): Interaction?
    suspend fun getResourceInteractions(tenantId: Uuid, resourceId: Uuid): List<Interaction>

    suspend fun registerFollowUp(tenantId: Uuid, followUp: RegisterFollowUp): RegisterFollowUpResult
    suspend fun getFollowUp(tenantId: Uuid, followUpId: Uuid): FollowUp?
    suspend fun getResourceFollowUps(tenantId: Uuid, resourceId: Uuid): List<FollowUp>
    suspend fun getOpenFollowUpsDueBy(tenantId: Uuid, dueOn: LocalDate): List<FollowUp>
    suspend fun rescheduleFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        dueOn: LocalDate,
    ): RescheduleFollowUpResult
    suspend fun changeFollowUpCadence(
        tenantId: Uuid,
        followUpId: Uuid,
        cadence: FollowUpCadence,
    ): ChangeFollowUpCadenceResult
    suspend fun completeFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        completedOn: LocalDate,
    ): CompleteFollowUpResult
    suspend fun cancelFollowUp(tenantId: Uuid, followUpId: Uuid): CancelFollowUpResult
}
