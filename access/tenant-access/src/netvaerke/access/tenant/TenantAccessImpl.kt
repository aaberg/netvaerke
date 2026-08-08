package netvaerke.access.tenant

import kotlin.uuid.Uuid
import netvaerke.access.tenant.repository.TenantEntity
import netvaerke.access.tenant.repository.TenantMemberEntity
import netvaerke.access.tenant.repository.TenantRepository

class TenantAccessImpl(
    private val repository: TenantRepository,
) : TenantAccess {
    override fun getTenant(id: Uuid): Tenant? =
        repository.getTenant(id)?.toTenant()

    override fun getUserTenants(userId: Uuid): List<TenantMember> =
        repository.getUserTenants(userId).map { it.toTenantMember() }

    override fun getTenantMembers(tenantId: Uuid): List<TenantMember> =
        repository.getTenantMembers(tenantId).map { it.toTenantMember() }

    override fun registerTenant(tenant: Tenant, tenantMembers: List<TenantMember>) {
        repository.registerTenant(tenant.toEntity(), tenantMembers.map { it.toEntity() })
    }

    override fun addTenantMember(tenantMember: TenantMember) {
        repository.addTenantMember(tenantMember.toEntity())
    }

    override fun removeTenantMember(tenantMember: TenantMember) {
        repository.removeTenantMember(tenantMember.toEntity())
    }

    private fun Tenant.toEntity(): TenantEntity = TenantEntity(
        id = id,
        type = type,
        name = name,
        owners = owners,
    )

    private fun TenantEntity.toTenant(): Tenant = Tenant(
        id = id,
        type = type,
        name = name,
        owners = owners,
    )

    private fun TenantMember.toEntity(): TenantMemberEntity = TenantMemberEntity(
        userId = userId,
        tenantId = tenantId,
        role = role,
    )

    private fun TenantMemberEntity.toTenantMember(): TenantMember = TenantMember(
        userId = userId,
        tenantId = tenantId,
        role = role,
    )
}
