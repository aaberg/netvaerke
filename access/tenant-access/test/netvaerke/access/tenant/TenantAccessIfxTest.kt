package netvaerke.access.tenant

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import netvaerke.ifx.DirectTransport
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport
import netvaerke.testsupport.NatsTestBroker

class TenantAccessIfxTest {
    @Test
    fun `calls tenant access directly through IFX`() = runBlocking {
        Ifx {
            service<TenantAccess> {
                via(DirectTransport)
            }
        }.use { ifx ->
            ifx.expose<TenantAccess>(InMemoryTenantAccess())
            ifx.start()

            exercise(ifx.create())
        }
    }

    @Test
    fun `calls tenant access through NATS`() = runBlocking {
        NatsTestBroker.openConnection().use { connection ->
            Ifx {
                service<TenantAccess> {
                    via(NatsTransport(connection).requestReply("tenant-access.${UUID.randomUUID()}"))
                }
            }.use { ifx ->
                ifx.expose<TenantAccess>(InMemoryTenantAccess())
                ifx.start()

                exercise(ifx.create())
            }
        }
    }

    private suspend fun exercise(access: TenantAccess) {
        val ownerId = randomUuid()
        val tenant = Tenant(
            id = randomUuid(),
            type = TenantType.ORGANIZATION,
            name = "Analytical Engines Ltd.",
            owners = listOf(ownerId),
        )
        val owner = TenantMember(ownerId, tenant.id, TenantMemberRole.OWNER)

        access.registerTenant(RegisterTenantRequest(tenant, listOf(owner)))

        assertEquals(tenant, access.getTenant(GetTenantRequest(tenant.id)))
        assertEquals(listOf(owner), access.getTenantMembers(GetTenantMembersRequest(tenant.id)))
        assertEquals(listOf(owner), access.getUserTenants(GetUserTenantsRequest(ownerId)))

        val member = TenantMember(randomUuid(), tenant.id, TenantMemberRole.MEMBER)
        access.addTenantMember(AddTenantMemberRequest(member))
        assertEquals(listOf(owner, member), access.getTenantMembers(GetTenantMembersRequest(tenant.id)))

        access.removeTenantMember(RemoveTenantMemberRequest(member))
        assertEquals(listOf(owner), access.getTenantMembers(GetTenantMembersRequest(tenant.id)))
    }
}

private class InMemoryTenantAccess : TenantAccess {
    private val tenant = AtomicReference<Tenant?>()
    private val members = AtomicReference<List<TenantMember>>(emptyList())

    override suspend fun getTenant(request: GetTenantRequest): Tenant? {
        requireNotNull(currentCoroutineContext()[Job])
        yield()
        return tenant.get()?.takeIf { it.id == request.id }
    }

    override suspend fun getUserTenants(request: GetUserTenantsRequest): List<TenantMember> =
        members.get().filter { it.userId == request.userId }

    override suspend fun getTenantMembers(request: GetTenantMembersRequest): List<TenantMember> =
        members.get().filter { it.tenantId == request.tenantId }

    override suspend fun registerTenant(request: RegisterTenantRequest) {
        tenant.set(request.tenant)
        members.set(request.tenantMembers)
    }

    override suspend fun addTenantMember(request: AddTenantMemberRequest) {
        members.updateAndGet { it + request.tenantMember }
    }

    override suspend fun removeTenantMember(request: RemoveTenantMemberRequest) {
        members.updateAndGet { current ->
            current.filterNot {
                it.tenantId == request.tenantMember.tenantId && it.userId == request.tenantMember.userId
            }
        }
    }
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
