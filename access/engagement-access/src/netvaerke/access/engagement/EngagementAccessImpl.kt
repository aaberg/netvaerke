package netvaerke.access.engagement

import java.time.LocalDate
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import netvaerke.access.engagement.repository.FollowUpRepository
import netvaerke.access.engagement.repository.InteractionEntity
import netvaerke.access.engagement.repository.InteractionRepository

class EngagementAccessImpl(
    private val interactionRepository: InteractionRepository,
    private val followUpRepository: FollowUpRepository,
    private val jdbcDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EngagementAccess {
    override suspend fun registerInteraction(tenantId: Uuid, interaction: Interaction): Boolean =
        withContext(jdbcDispatcher) {
            interactionRepository.registerInteraction(interaction.toEntity(tenantId))
        }

    override suspend fun updateInteraction(tenantId: Uuid, interaction: Interaction): Boolean =
        withContext(jdbcDispatcher) {
            interactionRepository.updateInteraction(interaction.toEntity(tenantId))
        }

    override suspend fun deleteInteraction(tenantId: Uuid, interactionId: Uuid): Boolean =
        withContext(jdbcDispatcher) {
            interactionRepository.deleteInteraction(tenantId, interactionId)
        }

    override suspend fun getInteraction(tenantId: Uuid, interactionId: Uuid): Interaction? =
        withContext(jdbcDispatcher) {
            interactionRepository.getInteraction(tenantId, interactionId)
        }?.toInteraction()

    override suspend fun getResourceInteractions(tenantId: Uuid, resourceId: Uuid): List<Interaction> =
        withContext(jdbcDispatcher) {
            interactionRepository.getResourceInteractions(tenantId, resourceId)
        }.map { it.toInteraction() }

    override suspend fun registerFollowUp(
        tenantId: Uuid,
        followUp: RegisterFollowUp,
    ): RegisterFollowUpResult = withContext(jdbcDispatcher) {
        followUpRepository.registerFollowUp(tenantId, followUp)
    }

    override suspend fun getFollowUp(tenantId: Uuid, followUpId: Uuid): FollowUp? =
        withContext(jdbcDispatcher) {
            followUpRepository.getFollowUp(tenantId, followUpId)
        }

    override suspend fun getResourceFollowUps(tenantId: Uuid, resourceId: Uuid): List<FollowUp> =
        withContext(jdbcDispatcher) {
            followUpRepository.getResourceFollowUps(tenantId, resourceId)
        }

    override suspend fun getOpenFollowUpsDueBy(tenantId: Uuid, dueOn: LocalDate): List<FollowUp> =
        withContext(jdbcDispatcher) {
            followUpRepository.getOpenFollowUpsDueBy(tenantId, dueOn)
        }

    override suspend fun rescheduleFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        dueOn: LocalDate,
    ): RescheduleFollowUpResult = withContext(jdbcDispatcher) {
        followUpRepository.rescheduleFollowUp(tenantId, followUpId, dueOn)
    }

    override suspend fun changeFollowUpCadence(
        tenantId: Uuid,
        followUpId: Uuid,
        cadence: FollowUpCadence,
    ): ChangeFollowUpCadenceResult = withContext(jdbcDispatcher) {
        followUpRepository.changeFollowUpCadence(tenantId, followUpId, cadence)
    }

    override suspend fun completeFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        completedOn: LocalDate,
    ): CompleteFollowUpResult = withContext(jdbcDispatcher) {
        followUpRepository.completeFollowUp(tenantId, followUpId, completedOn)
    }

    override suspend fun cancelFollowUp(tenantId: Uuid, followUpId: Uuid): CancelFollowUpResult =
        withContext(jdbcDispatcher) {
            followUpRepository.cancelFollowUp(tenantId, followUpId)
        }

    private fun Interaction.toEntity(tenantId: Uuid): InteractionEntity = InteractionEntity(
        id = id,
        tenantId = tenantId,
        resourceId = resourceId,
        userId = userId,
        channel = channel,
        notes = notes,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    private fun InteractionEntity.toInteraction(): Interaction = Interaction(
        id = id,
        resourceId = resourceId,
        userId = userId,
        channel = channel,
        notes = notes,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )
}
