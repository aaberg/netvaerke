package netvaerke.access.tenant

import kotlin.uuid.Uuid

interface TenantAccess {

    /**
     * Retrieves a tenant associated with the given identifier.
     *
     * @param id the unique identifier of the tenant to retrieve
     * @return the tenant corresponding to the given identifier, or null if no tenant is found
     */
    fun getTenant(id: Uuid): Tenant?

    /**
     * Retrieves a list of tenant memberships associated with the specified user.
     *
     * @param userId the unique identifier of the user whose tenant memberships are to be retrieved
     * @return a list of tenant memberships indicating the user's roles in various tenants
     */
    fun getUserTenants(userId: Uuid): List<TenantMember>

    /**
     * Retrieves the list of members associated with a specific tenant.
     *
     * @param tenantId the unique identifier of the tenant whose members are to be retrieved
     * @return a list of tenant members for the specified tenant, including their roles
     */
    fun getTenantMembers(tenantId: Uuid): List<TenantMember>

    /**
     * Registers a new tenant along with its associated members.
     *
     * @param tenant the tenant to be registered, including its details such as ID, type, name, and owners
     * @param tenantMembers a list of members associated with the tenant, including their roles within the tenant
     */
    fun registerTenant(tenant: Tenant, tenantMembers: List<TenantMember>)

    /**
     * Adds a new member to a tenant.
     *
     * @param tenantMember the tenant member to be added, including the user's ID, tenant's ID, and role within the tenant
     */
    fun addTenantMember(tenantMember: TenantMember)

    /**
     * Removes a member from a tenant.
     *
     * @param tenantMember the tenant member to be removed, including the user's ID, tenant's ID, and role within the tenant
     */
    fun removeTenantMember(tenantMember: TenantMember)

}