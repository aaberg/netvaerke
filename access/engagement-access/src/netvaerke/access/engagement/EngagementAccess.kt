package netvaerke.access.engagement

import kotlin.uuid.Uuid

interface EngagementAccess {
    suspend fun registerInteraction(tenantId: Uuid, interaction: Interaction): Boolean
    suspend fun updateInteraction(tenantId: Uuid, interaction: Interaction): Boolean
    suspend fun deleteInteraction(tenantId: Uuid, interactionId: Uuid): Boolean
    suspend fun getInteraction(tenantId: Uuid, interactionId: Uuid): Interaction?
    suspend fun getResourceInteractions(tenantId: Uuid, resourceId: Uuid): List<Interaction>
}
