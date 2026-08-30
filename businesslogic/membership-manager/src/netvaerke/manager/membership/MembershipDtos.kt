package netvaerke.manager.membership

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ProfileDto(
    val userId: Uuid,
    val name: String,
    val email: String,
)

@Serializable
data class TenantDto(
    val id: Uuid,
    val type: TenantTypeDto,
    val name: String,
    val owners: List<Uuid>,
)

@Serializable
enum class TenantTypeDto {
    PERSONAL,
    ORGANIZATION,
}
