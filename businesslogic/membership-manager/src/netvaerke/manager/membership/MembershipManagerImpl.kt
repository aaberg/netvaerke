package netvaerke.manager.membership

import kotlin.uuid.Uuid
import netvaerke.access.profile.Profile
import netvaerke.access.profile.ProfileAccess
import netvaerke.access.tenant.GetTenantRequest
import netvaerke.access.tenant.GetUserTenantsRequest
import netvaerke.access.tenant.RegisterTenantRequest
import netvaerke.access.tenant.Tenant
import netvaerke.access.tenant.TenantAccess
import netvaerke.access.tenant.TenantMember
import netvaerke.access.tenant.TenantMemberRole
import netvaerke.access.tenant.TenantType

class MembershipManagerImpl(
    private val profileAccess: ProfileAccess,
    private val tenantAccess: TenantAccess,
) : MembershipManager {
    override suspend fun registerProfileWithPersonalTenant(registerProfileRequest: RegisterProfileRequest) {
        val userId = registerProfileRequest.userId
        val tenant = Tenant(
            id = userId,
            type = TenantType.PERSONAL,
            name = registerProfileRequest.name,
            owners = listOf(userId),
        )
        val owner = TenantMember(
            userId = userId,
            tenantId = tenant.id,
            role = TenantMemberRole.OWNER,
        )

        tenantAccess.registerTenant(RegisterTenantRequest(tenant, listOf(owner)))
        profileAccess.registerProfile(
            Profile(
                userId = userId,
                name = registerProfileRequest.name,
                email = registerProfileRequest.email,
            ),
        )
    }

    override suspend fun getProfile(getProfileRequest: GetProfileRequest): GetProfileResponse {
        val profile = profileAccess.getProfile(getProfileRequest.userId)
            ?: throw ProfileNotFoundException(getProfileRequest.userId)
        val tenants = tenantAccess.getUserTenants(GetUserTenantsRequest(getProfileRequest.userId)).map { membership ->
            checkNotNull(tenantAccess.getTenant(GetTenantRequest(membership.tenantId))) {
                "Tenant ${membership.tenantId} referenced by user ${membership.userId} does not exist"
            }
        }

        return GetProfileResponse(profile, tenants)
    }
}

class ProfileNotFoundException(userId: Uuid) : NoSuchElementException("Profile for user $userId does not exist")
