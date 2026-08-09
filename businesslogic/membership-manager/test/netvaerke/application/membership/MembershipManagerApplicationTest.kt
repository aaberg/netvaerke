package netvaerke.application.membership

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import netvaerke.access.profile.Profile
import netvaerke.access.tenant.Tenant
import netvaerke.access.tenant.TenantType
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport
import netvaerke.manager.membership.GetProfileRequest
import netvaerke.manager.membership.GetProfileResponse
import netvaerke.manager.membership.MembershipManager
import netvaerke.manager.membership.RegisterProfileRequest
import netvaerke.testsupport.NatsTestBroker
import netvaerke.testsupport.PostgresTestDatabase

class MembershipManagerApplicationTest {
    private val dataSource: DataSource
        get() = MembershipApplicationTestDatabase.dataSource

    @BeforeTest
    fun clearMembershipData() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("TRUNCATE TABLE profile.profile, tenant.tenant CASCADE")
            }
        }
    }

    @Test
    fun `serves membership manager over NATS with direct access components`() = runBlocking {
        val subject = "membership-manager.${UUID.randomUUID()}"
        val config = ApplicationConfig(
            databaseUrl = "unused",
            databaseUser = "unused",
            databasePassword = "unused",
            natsUrl = "unused",
            membershipSubject = subject,
            membershipQueueGroup = subject,
            natsRequestTimeout = 5.seconds,
        )

        NatsTestBroker.openConnection().use { serverConnection ->
            NatsTestBroker.openConnection().use { clientConnection ->
                createMembershipManagerIfx(dataSource, serverConnection, config).use { serverIfx ->
                    Ifx {
                        service<MembershipManager> {
                            via(NatsTransport(clientConnection).requestReply(subject))
                        }
                    }.use { clientIfx ->
                        serverIfx.start()
                        clientIfx.start()
                        val client = clientIfx.create<MembershipManager>()
                        val request = RegisterProfileRequest(
                            userId = randomUuid(),
                            name = "Ada Lovelace",
                            email = "ada@example.com",
                        )

                        client.registerProfileWithPersonalTenant(request)

                        assertEquals(
                            GetProfileResponse(
                                profile = Profile(request.userId, request.name, request.email),
                                tenants = listOf(
                                    Tenant(
                                        id = request.userId,
                                        type = TenantType.PERSONAL,
                                        name = request.name,
                                        owners = listOf(request.userId),
                                    ),
                                ),
                            ),
                            client.getProfile(GetProfileRequest(request.userId)),
                        )
                    }
                }
            }
        }
    }
}

private object MembershipApplicationTestDatabase {
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
