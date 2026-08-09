package netvaerke.manager.membership

import netvaerke.access.profile.Profile
import netvaerke.access.tenant.Tenant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
data class GetProfileRequest(
    val userId: Uuid,
)

@Serializable
data class GetProfileResponse(
    val profile: Profile,
    val tenants: List<Tenant>,
)
