package netvaerke.access.tenant

import kotlin.uuid.Uuid

data class TenantMember(
    val userId: Uuid,
    val tenantId: Uuid,
    val role: TenantMemberRole
)
