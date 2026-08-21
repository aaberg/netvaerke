package netvaerke.engine.authorization

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
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

class AuthorizationEngineImplTest {
    @Test
    fun `authorizes owners and members for every contact operation`() = runBlocking {
        val tenantId = randomUuid()
        val ownerId = randomUuid()
        val memberId = randomUuid()
        val tenantAccess = RecordingTenantAccess(
            memberships = mutableListOf(
                TenantMember(ownerId, tenantId, TenantMemberRole.OWNER),
                TenantMember(memberId, tenantId, TenantMemberRole.MEMBER),
            ),
        )
        val engine = AuthorizationEngineImpl(tenantAccess)

        listOf(ownerId, memberId).forEach { actorId ->
            Operation.entries.forEach { operation ->
                assertTrue(engine.authorize(actorId, tenantId, operation).authorized)
            }
        }

        assertEquals(
            listOf(ownerId, ownerId, memberId, memberId),
            tenantAccess.requestedUserIds,
        )
    }

    @Test
    fun `uses current membership for the requested tenant`() = runBlocking {
        val actorId = randomUuid()
        val tenantId = randomUuid()
        val tenantAccess = RecordingTenantAccess(
            memberships = mutableListOf(TenantMember(actorId, randomUuid(), TenantMemberRole.MEMBER)),
        )
        val engine = AuthorizationEngineImpl(tenantAccess)

        assertFalse(engine.authorize(actorId, tenantId, Operation.ReadContacts).authorized)

        tenantAccess.memberships.clear()

        assertFalse(engine.authorize(actorId, tenantId, Operation.UpdateContacts).authorized)

        tenantAccess.memberships += TenantMember(actorId, tenantId, TenantMemberRole.MEMBER)

        assertTrue(engine.authorize(actorId, tenantId, Operation.ReadContacts).authorized)

        tenantAccess.memberships.clear()

        assertFalse(engine.authorize(actorId, tenantId, Operation.UpdateContacts).authorized)
        assertEquals(listOf(actorId, actorId, actorId, actorId), tenantAccess.requestedUserIds)
    }
}

private class RecordingTenantAccess(
    val memberships: MutableList<TenantMember>,
    val requestedUserIds: MutableList<Uuid> = mutableListOf(),
) : TenantAccess {
    override suspend fun getTenant(request: GetTenantRequest): Tenant? =
        throw UnsupportedOperationException()

    override suspend fun getUserTenants(request: GetUserTenantsRequest): List<TenantMember> {
        requestedUserIds += request.userId
        return memberships.filter { it.userId == request.userId }
    }

    override suspend fun getTenantMembers(request: GetTenantMembersRequest): List<TenantMember> =
        throw UnsupportedOperationException()

    override suspend fun registerTenant(request: RegisterTenantRequest) {
        throw UnsupportedOperationException()
    }

    override suspend fun addTenantMember(request: AddTenantMemberRequest) {
        throw UnsupportedOperationException()
    }

    override suspend fun removeTenantMember(request: RemoveTenantMemberRequest) {
        throw UnsupportedOperationException()
    }
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
