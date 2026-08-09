package netvaerke.access.tenant

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import netvaerke.access.tenant.repository.TenantEntity
import netvaerke.access.tenant.repository.TenantMemberEntity
import netvaerke.access.tenant.repository.TenantRepository

class TenantAccessImpl(
    private val repository: TenantRepository,
    private val jdbcDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TenantAccess {
    override suspend fun getTenant(request: GetTenantRequest): Tenant? =
        withContext(jdbcDispatcher) {
            repository.getTenant(request.id)
        }?.toTenant()

    override suspend fun getUserTenants(request: GetUserTenantsRequest): List<TenantMember> =
        withContext(jdbcDispatcher) {
            repository.getUserTenants(request.userId)
        }.map { it.toTenantMember() }

    override suspend fun getTenantMembers(request: GetTenantMembersRequest): List<TenantMember> =
        withContext(jdbcDispatcher) {
            repository.getTenantMembers(request.tenantId)
        }.map { it.toTenantMember() }

    override suspend fun registerTenant(request: RegisterTenantRequest) {
        withContext(jdbcDispatcher) {
            repository.registerTenant(
                request.tenant.toEntity(),
                request.tenantMembers.map { it.toEntity() },
            )
        }
    }

    override suspend fun addTenantMember(request: AddTenantMemberRequest) {
        withContext(jdbcDispatcher) {
            repository.addTenantMember(request.tenantMember.toEntity())
        }
    }

    override suspend fun removeTenantMember(request: RemoveTenantMemberRequest) {
        withContext(jdbcDispatcher) {
            repository.removeTenantMember(request.tenantMember.toEntity())
        }
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
