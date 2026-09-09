package netvaerke.application.network

import io.nats.client.Nats
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.runBlocking
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import netvaerke.access.tenant.RegisterTenantRequest
import netvaerke.access.tenant.Tenant
import netvaerke.access.tenant.TenantAccessImpl
import netvaerke.access.tenant.TenantMember
import netvaerke.access.tenant.TenantMemberRole
import netvaerke.access.tenant.TenantType
import netvaerke.access.tenant.repository.TenantRepository
import netvaerke.manager.network.CreateNewContactDto
import netvaerke.manager.network.CreateContactInteractionDto
import netvaerke.manager.network.EmailAddressDto
import netvaerke.manager.network.NetworkManager
import netvaerke.manager.network.InteractionChannelDto
import netvaerke.manager.network.UpdateContactInteractionDto
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport
import netvaerke.testsupport.NatsTestBroker
import netvaerke.testsupport.PostgresTestDatabase
import org.postgresql.ds.PGSimpleDataSource

class NetworkManagerApplicationTest {
    private val dataSource: DataSource
        get() = NetworkManagerApplicationTestDatabase.dataSource

    @BeforeTest
    fun clearNetworkData() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("TRUNCATE TABLE engagement.interaction, contact.contact, tenant.tenant CASCADE")
            }
        }
    }

    @AfterTest
    fun dropNetworkDatabase() {
        NetworkManagerApplicationTestDatabase.drop()
    }

    @Test
    fun `serves network manager over NATS with direct access components`() = runBlocking {
        val subject = "network-manager.${UUID.randomUUID()}"
        val config = ApplicationConfig(
            databaseUrl = "unused",
            databaseUser = "unused",
            databasePassword = "unused",
            natsUrl = "unused",
            networkManagerSubject = subject,
            networkManagerQueueGroup = subject,
            natsRequestTimeout = 5.seconds,
        )
        val tenantId = randomUuid()
        val actorId = randomUuid()
        TenantAccessImpl(TenantRepository(dataSource)).registerTenant(
            RegisterTenantRequest(
                tenant = Tenant(tenantId, TenantType.PERSONAL, "Ada's network", listOf(actorId)),
                tenantMembers = listOf(TenantMember(actorId, tenantId, TenantMemberRole.OWNER)),
            ),
        )

        NatsTestBroker.openConnection().use { serverConnection ->
            NatsTestBroker.openConnection().use { clientConnection ->
                createNetworkManagerIfx(dataSource, serverConnection, config).use { serverIfx ->
                    Ifx {
                        service<NetworkManager> {
                            via(NatsTransport(clientConnection).requestReply(subject))
                        }
                    }.use { clientIfx ->
                        serverIfx.start()
                        clientIfx.start()
                        val client = clientIfx.create<NetworkManager>()

                        val created = client.createNewContact(
                            tenantId,
                            actorId,
                            CreateNewContactDto(
                                name = "Grace Hopper",
                                emails = listOf(EmailAddressDto("grace@example.test", isPrimary = true)),
                                phoneNumbers = emptyList(),
                                workInfo = null,
                                note = null,
                            ),
                        )

                        assertEquals("Grace Hopper", created.name)
                        assertEquals(
                            listOf(created.contactId),
                            client.getTenantContacts(tenantId, actorId).map { it.contactId },
                        )

                        val upload = client.reserveContactImageUpload(tenantId, actorId, created.contactId)
                        assertTrue(upload.fileKey.startsWith("tenants/$tenantId/contacts/${created.contactId}/images/"))
                        assertEquals(null, client.setContactImage(tenantId, actorId, created.contactId, null).previousFileKey)

                        val interaction = client.registerContactInteraction(
                            tenantId,
                            actorId,
                            created.contactId,
                            CreateContactInteractionDto(
                                channel = InteractionChannelDto.EMAIL,
                                notes = "Sent a follow-up.",
                                occurredAt = "2026-09-09T10:00:00Z",
                            ),
                        )
                        assertEquals(
                            listOf(interaction),
                            client.getContactOverview(tenantId, actorId, created.contactId)?.interactions,
                        )

                        client.updateContactInteraction(
                            tenantId,
                            actorId,
                            created.contactId,
                            interaction.interactionId,
                            UpdateContactInteractionDto(
                                channel = InteractionChannelDto.PHONE,
                                notes = "Discussed the proposal.",
                                occurredAt = "2026-09-10T10:00:00Z",
                            ),
                        )
                        assertEquals(
                            InteractionChannelDto.PHONE,
                            client.getContactOverview(tenantId, actorId, created.contactId)?.interactions?.single()?.channel,
                        )

                        client.removeContactInteraction(
                            tenantId,
                            actorId,
                            created.contactId,
                            interaction.interactionId,
                        )
                        assertEquals(emptyList(), client.getContactOverview(tenantId, actorId, created.contactId)?.interactions)
                    }
                }
            }
        }
    }
}

private object NetworkManagerApplicationTestDatabase {
    private val databaseName: String = requireNotNull("network_manager_${UUID.randomUUID()}".replace("-", "_"))
    private val dataSourceDelegate = lazy {
        val sharedDataSource = PostgresTestDatabase.dataSource() as PGSimpleDataSource
        sharedDataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE DATABASE $databaseName")
            }
        }
        val sharedUrl: String = checkNotNull(sharedDataSource.getURL())
        val isolatedUrl: String = databaseUrl(sharedUrl, databaseName)
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
