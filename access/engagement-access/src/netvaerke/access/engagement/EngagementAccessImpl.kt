package netvaerke.access.engagement

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import netvaerke.access.engagement.repository.InteractionEntity
import netvaerke.access.engagement.repository.InteractionRepository
import kotlin.uuid.Uuid

class EngagementAccessImpl(
    private val repository: InteractionRepository,
    private val jdbcDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EngagementAccess {
    override suspend fun registerInteraction(tenantId: Uuid, interaction: Interaction): Boolean =
        withContext(jdbcDispatcher) {
            repository.registerInteraction(interaction.toEntity(tenantId))
        }

    override suspend fun updateInteraction(tenantId: Uuid, interaction: Interaction): Boolean =
        withContext(jdbcDispatcher) {
            repository.updateInteraction(interaction.toEntity(tenantId))
        }

    override suspend fun deleteInteraction(tenantId: Uuid, interactionId: Uuid): Boolean =
        withContext(jdbcDispatcher) {
            repository.deleteInteraction(tenantId, interactionId)
        }

    override suspend fun getInteraction(tenantId: Uuid, interactionId: Uuid): Interaction? =
        withContext(jdbcDispatcher) {
            repository.getInteraction(tenantId, interactionId)
        }?.toInteraction()

    override suspend fun getResourceInteractions(tenantId: Uuid, resourceId: Uuid): List<Interaction> =
        withContext(jdbcDispatcher) {
            repository.getResourceInteractions(tenantId, resourceId)
        }.map { it.toInteraction() }

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
