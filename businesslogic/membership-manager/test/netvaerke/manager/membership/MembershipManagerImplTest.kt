package netvaerke.manager.membership

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import netvaerke.access.profile.Profile
import netvaerke.access.profile.ProfileAccess
import netvaerke.access.tenant.AddTenantMemberRequest
import netvaerke.access.tenant.GetTenantMembersRequest
import netvaerke.access.tenant.GetTenantRequest
import netvaerke.access.tenant.GetUserTenantsRequest
import netvaerke.access.tenant.RegisterTenantRequest
import netvaerke.access.tenant.RemoveTenantMemberRequest
import netvaerke.access.tenant.Tenant
import netvaerke.access.tenant.TenantAccess
import netvaerke.access.tenant.TenantMember
import netvaerke.access.tenant.TenantMemberRole
import netvaerke.access.tenant.TenantType

class MembershipManagerImplTest {
    @Test
    fun `registers a profile and an idempotent personal tenant`() = runBlocking {
        val profileAccess = InMemoryProfileAccess()
        val tenantAccess = InMemoryTenantAccess()
        val manager = MembershipManagerImpl(profileAccess, tenantAccess)
        val request = RegisterProfileRequest(
            userId = randomUuid(),
            name = "Ada Lovelace",
            email = "ada@example.com",
        )

        manager.registerProfileWithPersonalTenant(request)
        manager.registerProfileWithPersonalTenant(request)

        assertEquals(Profile(request.userId, request.name, request.email), profileAccess.profile)
        assertEquals(
            Tenant(request.userId, TenantType.PERSONAL, request.name, listOf(request.userId)),
            tenantAccess.tenants.single(),
        )
        assertEquals(
            TenantMember(request.userId, request.userId, TenantMemberRole.OWNER),
            tenantAccess.members.single(),
        )
    }

    @Test
    fun `a retry completes registration after the profile write fails`() = runBlocking {
        val profileAccess = InMemoryProfileAccess(failedRegistrations = 1)
        val tenantAccess = InMemoryTenantAccess()
        val manager = MembershipManagerImpl(profileAccess, tenantAccess)
        val request = RegisterProfileRequest(
            userId = randomUuid(),
            name = "Ada Lovelace",
            email = "ada@example.com",
        )

        assertFailsWith<IllegalStateException> {
            manager.registerProfileWithPersonalTenant(request)
        }
        assertEquals(null, profileAccess.profile)
        assertEquals(listOf(request.userId), tenantAccess.tenants.map(Tenant::id))

        manager.registerProfileWithPersonalTenant(request)

        assertEquals(Profile(request.userId, request.name, request.email), profileAccess.profile)
        assertEquals(listOf(request.userId), tenantAccess.tenants.map(Tenant::id))
    }

    @Test
    fun `returns a profile and all of its tenants`() = runBlocking {
        val userId = randomUuid()
        val profile = Profile(userId, "Grace Hopper", "grace@example.com")
        val personalTenant = Tenant(userId, TenantType.PERSONAL, profile.name, listOf(userId))
        val organizationTenant = Tenant(randomUuid(), TenantType.ORGANIZATION, "Compilers Inc.", listOf(userId))
        val profileAccess = InMemoryProfileAccess(profile)
        val tenantAccess = InMemoryTenantAccess(
            tenants = mutableListOf(personalTenant, organizationTenant),
            members = mutableListOf(
                TenantMember(userId, personalTenant.id, TenantMemberRole.OWNER),
                TenantMember(userId, organizationTenant.id, TenantMemberRole.OWNER),
            ),
        )

        val response = MembershipManagerImpl(profileAccess, tenantAccess).getProfile(GetProfileRequest(userId))

        assertEquals(GetProfileResponse(profile, listOf(personalTenant, organizationTenant)), response)
    }

    @Test
    fun `fails when the profile does not exist`() = runBlocking {
        val userId = randomUuid()

        assertFailsWith<ProfileNotFoundException> {
            MembershipManagerImpl(InMemoryProfileAccess(), InMemoryTenantAccess()).getProfile(GetProfileRequest(userId))
        }
        Unit
    }

    @Test
    fun `fails when a membership references a missing tenant`() = runBlocking {
        val userId = randomUuid()
        val profile = Profile(userId, "Katherine Johnson", "katherine@example.com")
        val missingTenantId = randomUuid()
        val tenantAccess = InMemoryTenantAccess(
            members = mutableListOf(TenantMember(userId, missingTenantId, TenantMemberRole.MEMBER)),
        )

        val failure = assertFailsWith<IllegalStateException> {
            MembershipManagerImpl(InMemoryProfileAccess(profile), tenantAccess).getProfile(GetProfileRequest(userId))
        }

        assertEquals(
            "Tenant $missingTenantId referenced by user $userId does not exist",
            failure.message,
        )
    }
}

private class InMemoryProfileAccess(
    var profile: Profile? = null,
    private var failedRegistrations: Int = 0,
) : ProfileAccess {
    override suspend fun getProfile(userId: Uuid): Profile? = profile?.takeIf { it.userId == userId }

    override suspend fun registerProfile(profile: Profile) {
        if (failedRegistrations > 0) {
            failedRegistrations -= 1
            error("Profile registration failed")
        }
        this.profile = profile
    }
}

private class InMemoryTenantAccess(
    val tenants: MutableList<Tenant> = mutableListOf(),
    val members: MutableList<TenantMember> = mutableListOf(),
) : TenantAccess {
    override suspend fun getTenant(request: GetTenantRequest): Tenant? =
        tenants.singleOrNull { it.id == request.id }

    override suspend fun getUserTenants(request: GetUserTenantsRequest): List<TenantMember> =
        members.filter { it.userId == request.userId }

    override suspend fun getTenantMembers(request: GetTenantMembersRequest): List<TenantMember> =
        members.filter { it.tenantId == request.tenantId }

    override suspend fun registerTenant(request: RegisterTenantRequest) {
        tenants.removeAll { it.id == request.tenant.id }
        tenants += request.tenant
        request.tenantMembers.forEach { member ->
            members.removeAll { it.tenantId == member.tenantId && it.userId == member.userId }
            members += member
        }
    }

    override suspend fun addTenantMember(request: AddTenantMemberRequest) {
        members += request.tenantMember
    }

    override suspend fun removeTenantMember(request: RemoveTenantMemberRequest) {
        members.removeIf {
            it.tenantId == request.tenantMember.tenantId && it.userId == request.tenantMember.userId
        }
    }
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
