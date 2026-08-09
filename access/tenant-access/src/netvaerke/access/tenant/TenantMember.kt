package netvaerke.access.tenant

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
data class TenantMember(
    val userId: Uuid,
    val tenantId: Uuid,
    val role: TenantMemberRole,
)
