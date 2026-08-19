package netvaerke.engine.authorization

import kotlin.uuid.Uuid

interface AuthorizationEngine {

    suspend fun authorize(actorId: Uuid, tenantId: Uuid, operation: Operation) : AuthorizationResponseDto
}