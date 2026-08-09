package netvaerke.access.tenant

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
data class Tenant(
    val id: Uuid,
    val type: TenantType,
    val name: String,
    val owners: List<Uuid>,
)
