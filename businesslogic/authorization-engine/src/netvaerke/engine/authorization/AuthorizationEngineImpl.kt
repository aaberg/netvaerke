package netvaerke.engine.authorization

import kotlin.uuid.Uuid
import netvaerke.access.tenant.GetUserTenantsRequest
import netvaerke.access.tenant.TenantAccess
import netvaerke.access.tenant.TenantMemberRole

class AuthorizationEngineImpl(
    private val tenantAccess: TenantAccess,
) : AuthorizationEngine {
    override suspend fun authorize(
        actorId: Uuid,
        tenantId: Uuid,
        operation: Operation,
    ): AuthorizationResponseDto {
        val isTenantMember = tenantAccess.getUserTenants(GetUserTenantsRequest(actorId)).any { membership ->
            membership.tenantId == tenantId && when (membership.role) {
                TenantMemberRole.OWNER,
                TenantMemberRole.MEMBER,
                -> true
            }
        }

        val authorized = when (operation) {
            Operation.ReadContacts,
            Operation.UpdateContacts,
            -> isTenantMember
        }
        return AuthorizationResponseDto(authorized)
    }
}
