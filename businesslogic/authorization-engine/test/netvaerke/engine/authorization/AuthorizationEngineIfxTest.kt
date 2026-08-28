package netvaerke.engine.authorization

import java.util.UUID
import kotlin.test.Test
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
import netvaerke.ifx.DirectTransport
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport
import netvaerke.testsupport.NatsTestBroker

class AuthorizationEngineIfxTest {
    @Test
    fun `calls authorization engine directly through IFX`() = runBlocking {
        Ifx {
            service<AuthorizationEngine> {
                via(DirectTransport)
            }
        }.use { ifx ->
            ifx.expose<AuthorizationEngine>(AuthorizationEngineImpl(IfxTenantAccess()))
            ifx.start()

            exercise(ifx.create())
        }
    }

    @Test
    fun `calls authorization engine through NATS`() = runBlocking {
        NatsTestBroker.openConnection().use { connection ->
            Ifx {
                service<AuthorizationEngine> {
                    via(NatsTransport(connection).requestReply("authorization-engine.${UUID.randomUUID()}"))
                }
            }.use { ifx ->
                ifx.expose<AuthorizationEngine>(AuthorizationEngineImpl(IfxTenantAccess()))
                ifx.start()

                exercise(ifx.create())
            }
        }
    }

    private suspend fun exercise(engine: AuthorizationEngine) {
        val tenantId = randomUuid()
        val actorId = tenantId

        assertTrue(engine.authorize(actorId, tenantId, Operation.READ_CONTACTS).authorized)
        assertTrue(engine.authorize(actorId, tenantId, Operation.UPDATE_CONTACTS).authorized)
        assertFalse(engine.authorize(randomUuid(), tenantId, Operation.READ_CONTACTS).authorized)
    }
}

private class IfxTenantAccess : TenantAccess {
    override suspend fun getTenant(request: GetTenantRequest): Tenant? =
        throw UnsupportedOperationException()

    override suspend fun getUserTenants(request: GetUserTenantsRequest): List<TenantMember> =
        listOf(TenantMember(request.userId, request.userId, TenantMemberRole.OWNER))

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
