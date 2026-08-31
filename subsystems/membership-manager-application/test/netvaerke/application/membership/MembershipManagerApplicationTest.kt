package netvaerke.application.membership

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.AfterTest
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
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport
import netvaerke.manager.membership.GetProfileRequest
import netvaerke.manager.membership.GetProfileResponse
import netvaerke.manager.membership.MembershipManager
import netvaerke.manager.membership.ProfileDto
import netvaerke.manager.membership.RegisterProfileRequest
import netvaerke.manager.membership.TenantDto
import netvaerke.manager.membership.TenantTypeDto
import netvaerke.testsupport.NatsTestBroker
import netvaerke.testsupport.PostgresTestDatabase
import org.postgresql.ds.PGSimpleDataSource

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

    @AfterTest
    fun dropMembershipDatabase() {
        MembershipApplicationTestDatabase.drop()
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
                                profile = ProfileDto(request.userId, request.name, request.email),
                                tenants = listOf(
                                    TenantDto(
                                        id = request.userId,
                                        type = TenantTypeDto.PERSONAL,
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
    private val databaseName = "membership_manager_${UUID.randomUUID()}".replace("-", "_")
    private val dataSourceDelegate = lazy {
        val sharedDataSource = PostgresTestDatabase.dataSource() as PGSimpleDataSource
        sharedDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE DATABASE $databaseName")
            }
        }
        val sharedUrl = checkNotNull(sharedDataSource.getURL())
        val isolatedUrl = databaseUrl(sharedUrl, databaseName)
        PGSimpleDataSource().apply {
            setURL(isolatedUrl)
            user = sharedDataSource.getUser()
            password = sharedDataSource.getPassword()
        }.also(::runMigrations)
    }

    val dataSource: DataSource
        get() = dataSourceDelegate.value

    fun drop() {
        if (!dataSourceDelegate.isInitialized()) return
        PostgresTestDatabase.dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP DATABASE $databaseName WITH (FORCE)")
            }
        }
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

private fun databaseUrl(url: String, databaseName: String): String {
    val queryStart = url.indexOf('?')
    val baseUrl = if (queryStart < 0) url else url.substring(0, queryStart)
    val query = if (queryStart < 0) "" else url.substring(queryStart)
    return "${baseUrl.substringBeforeLast('/')}/$databaseName$query"
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
