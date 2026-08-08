package netvaerke.access.tenant.repository

import java.time.Instant
import kotlin.uuid.Uuid
import netvaerke.access.tenant.TenantMemberRole
import netvaerke.access.tenant.TenantType

data class TenantEntity(
    val id: Uuid,
    val type: TenantType,
    val name: String,
    val owners: List<Uuid>,
    val createdAt: Instant? = null,
)

data class TenantMemberEntity(
    val userId: Uuid,
    val tenantId: Uuid,
    val role: TenantMemberRole,
)
