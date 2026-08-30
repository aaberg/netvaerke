package netvaerke.manager.membership

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
data class GetProfileRequest(
    val userId: Uuid,
)

@Serializable
data class GetProfileResponse(
    val profile: ProfileDto,
    val tenants: List<TenantDto>,
)
