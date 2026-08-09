package netvaerke.access.tenant

import kotlinx.serialization.Serializable

@Serializable
enum class TenantMemberRole {
    OWNER,
    MEMBER,
}
