package netvaerke.manager.membership

import netvaerke.access.profile.Profile
import netvaerke.access.tenant.Tenant
import netvaerke.access.tenant.TenantType

internal fun Profile.toDto(): ProfileDto = ProfileDto(userId, name, email)

internal fun Tenant.toDto(): TenantDto = TenantDto(id, type.toDto(), name, owners)

private fun TenantType.toDto(): TenantTypeDto = when (this) {
    TenantType.PERSONAL -> TenantTypeDto.PERSONAL
    TenantType.ORGANIZATION -> TenantTypeDto.ORGANIZATION
}
