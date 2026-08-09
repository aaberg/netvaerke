package netvaerke.access.tenant

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

interface TenantAccess {

    /**
     * Retrieves a tenant associated with the given identifier.
     *
     * @param request identifies the tenant to retrieve
     * @return the tenant corresponding to the given identifier, or null if no tenant is found
     */
    suspend fun getTenant(request: GetTenantRequest): Tenant?

    /**
     * Retrieves a list of tenant memberships associated with the specified user.
     *
     * @param request identifies the user whose tenant memberships are to be retrieved
     * @return a list of tenant memberships indicating the user's roles in various tenants
     */
    suspend fun getUserTenants(request: GetUserTenantsRequest): List<TenantMember>

    /**
     * Retrieves the list of members associated with a specific tenant.
     *
     * @param request identifies the tenant whose members are to be retrieved
     * @return a list of tenant members for the specified tenant, including their roles
     */
    suspend fun getTenantMembers(request: GetTenantMembersRequest): List<TenantMember>

    /**
     * Registers a new tenant along with its associated members.
     *
     * @param request contains the tenant and its members
     */
    suspend fun registerTenant(request: RegisterTenantRequest)

    /**
     * Adds a new member to a tenant.
     *
     * @param request contains the tenant member to add
     */
    suspend fun addTenantMember(request: AddTenantMemberRequest)

    /**
     * Removes a member from a tenant.
     *
     * @param request contains the tenant member to remove
     */
    suspend fun removeTenantMember(request: RemoveTenantMemberRequest)

}

@Serializable
data class GetTenantRequest(
    val id: Uuid,
)

@Serializable
data class GetUserTenantsRequest(
    val userId: Uuid,
)

@Serializable
data class GetTenantMembersRequest(
    val tenantId: Uuid,
)

@Serializable
data class RegisterTenantRequest(
    val tenant: Tenant,
    val tenantMembers: List<TenantMember>,
)

@Serializable
data class AddTenantMemberRequest(
    val tenantMember: TenantMember,
)

@Serializable
data class RemoveTenantMemberRequest(
    val tenantMember: TenantMember,
)
