package netvaerke.access.tenant.repository

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import netvaerke.access.tenant.Tenant
import netvaerke.access.tenant.TenantAccess
import netvaerke.access.tenant.TenantAccessImpl
import netvaerke.access.tenant.AddTenantMemberRequest
import netvaerke.access.tenant.GetTenantMembersRequest
import netvaerke.access.tenant.GetTenantRequest
import netvaerke.access.tenant.GetUserTenantsRequest
import netvaerke.access.tenant.RegisterTenantRequest
import netvaerke.access.tenant.RemoveTenantMemberRequest
import netvaerke.access.tenant.TenantMember
import netvaerke.access.tenant.TenantMemberRole
import netvaerke.access.tenant.TenantType
import netvaerke.testsupport.PostgresTestDatabase

class TenantRepositoryTest {
    private val dataSource: DataSource
        get() = TenantTestDatabase.dataSource

    private val access: TenantAccess
        get() = TenantAccessImpl(TenantRepository(dataSource))

    @BeforeTest
    fun clearTenants() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("TRUNCATE TABLE tenant.tenant CASCADE")
            }
        }
    }

    @Test
    fun `registers a tenant and its members`() = runBlocking {
        val ownerId = randomUuid()
        val memberId = randomUuid()
        val tenant = Tenant(
            id = randomUuid(),
            type = TenantType.ORGANIZATION,
            name = "Analytical Engines Ltd.",
            owners = listOf(ownerId),
        )
        val members = listOf(
            TenantMember(ownerId, tenant.id, TenantMemberRole.OWNER),
            TenantMember(memberId, tenant.id, TenantMemberRole.MEMBER),
        )

        access.registerTenant(RegisterTenantRequest(tenant, members))

        assertEquals(tenant, access.getTenant(GetTenantRequest(tenant.id)))
        assertEquals(
            members.sortedBy { it.userId.toString() },
            access.getTenantMembers(GetTenantMembersRequest(tenant.id)),
        )
        assertEquals(listOf(members[1]), access.getUserTenants(GetUserTenantsRequest(memberId)))
        assertNull(access.getTenant(GetTenantRequest(randomUuid())))
    }

    @Test
    fun `updates and removes tenant members`() = runBlocking {
        val ownerId = randomUuid()
        val memberId = randomUuid()
        val tenant = Tenant(
            id = randomUuid(),
            type = TenantType.PERSONAL,
            name = "Ada Lovelace",
            owners = listOf(ownerId),
        )
        val owner = TenantMember(ownerId, tenant.id, TenantMemberRole.OWNER)
        val member = TenantMember(memberId, tenant.id, TenantMemberRole.MEMBER)
        access.registerTenant(RegisterTenantRequest(tenant, listOf(owner)))

        access.addTenantMember(AddTenantMemberRequest(member))
        val promotedMember = member.copy(role = TenantMemberRole.OWNER)
        access.addTenantMember(AddTenantMemberRequest(promotedMember))

        assertEquals(
            tenant.copy(owners = listOf(ownerId, memberId).sortedBy { it.toString() }),
            access.getTenant(GetTenantRequest(tenant.id)),
        )
        assertEquals(listOf(promotedMember), access.getUserTenants(GetUserTenantsRequest(memberId)))

        access.removeTenantMember(RemoveTenantMemberRequest(promotedMember))

        assertEquals(listOf(owner), access.getTenantMembers(GetTenantMembersRequest(tenant.id)))
        assertEquals(emptyList(), access.getUserTenants(GetUserTenantsRequest(memberId)))
    }

    @Test
    fun `rejects owners that do not match owner memberships`() = runBlocking {
        val ownerId = randomUuid()
        val tenant = Tenant(
            id = randomUuid(),
            type = TenantType.PERSONAL,
            name = "Ada Lovelace",
            owners = listOf(ownerId),
        )
        val nonOwnerMembership = TenantMember(ownerId, tenant.id, TenantMemberRole.MEMBER)

        val failure = assertFailsWith<IllegalArgumentException> {
            access.registerTenant(RegisterTenantRequest(tenant, listOf(nonOwnerMembership)))
        }

        assertTrue(failure.message.orEmpty().contains("owners must match"))
        assertNull(access.getTenant(GetTenantRequest(tenant.id)))
    }
}

private object TenantTestDatabase {
    val dataSource: DataSource by lazy {
        PostgresTestDatabase.dataSource().also(::runMigrations)
    }

    private fun runMigrations(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase(
                "changelog-root.yaml",
                DirectoryResourceAccessor(liquibaseDirectory()),
                database,
            ).use { liquibase ->
                liquibase.update(Contexts(), LabelExpression())
            }
        }
    }

    private fun liquibaseDirectory(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("liquibase") }
            .firstOrNull { Files.isRegularFile(it.resolve("changelog-root.yaml")) }
            ?: error("Could not locate the Liquibase changelog directory")
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
