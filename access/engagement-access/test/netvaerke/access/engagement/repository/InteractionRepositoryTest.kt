package netvaerke.access.engagement.repository

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import netvaerke.access.engagement.EngagementAccess
import netvaerke.access.engagement.EngagementAccessImpl
import netvaerke.access.engagement.Interaction
import netvaerke.access.engagement.InteractionChannel
import netvaerke.testsupport.PostgresTestDatabase

class InteractionRepositoryTest {
    private val dataSource: DataSource
        get() = EngagementTestDatabase.dataSource

    private val access: EngagementAccess
        get() = EngagementAccessImpl(InteractionRepository(dataSource))

    @BeforeTest
    fun clearInteractions() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("TRUNCATE TABLE engagement.interaction")
            }
        }
    }

    @Test
    fun `registers and returns contact interactions by occurrence time`() = runBlocking {
        val tenantId = randomUuid()
        val contactId = randomUuid()
        val earlier = interaction(
            contactId = contactId,
            occurredAt = Instant.parse("2026-08-20T10:00:00Z"),
            channel = InteractionChannel.EMAIL,
            notes = null,
        )
        val later = interaction(
            contactId = contactId,
            occurredAt = Instant.parse("2026-08-21T10:00:00Z"),
            channel = InteractionChannel.PHONE,
            notes = "Discussed the proposal",
        )

        assertEquals(true, access.registerInteraction(tenantId, earlier))
        assertEquals(true, access.registerInteraction(tenantId, later))

        val storedEarlier = assertNotNull(access.getInteraction(tenantId, earlier.id))
        val storedLater = assertNotNull(access.getInteraction(tenantId, later.id))
        assertEquals(earlier.copy(createdAt = storedEarlier.createdAt), storedEarlier)
        assertNotNull(storedEarlier.createdAt)
        assertEquals(
            listOf(storedLater, storedEarlier),
            access.getContactInteractions(tenantId, contactId),
        )
    }

    @Test
    fun `updates mutable interaction fields without changing attribution`() = runBlocking {
        val tenantId = randomUuid()
        val interaction = interaction(
            contactId = randomUuid(),
            occurredAt = Instant.parse("2026-08-20T10:00:00Z"),
            channel = InteractionChannel.TEXT,
            notes = null,
        )
        access.registerInteraction(tenantId, interaction)
        val updated = interaction.copy(
            contactId = randomUuid(),
            userId = randomUuid(),
            channel = InteractionChannel.IN_PERSON,
            notes = "Met at the conference",
            occurredAt = Instant.parse("2026-08-22T10:00:00Z"),
        )

        assertEquals(true, access.updateInteraction(tenantId, updated))

        val stored = assertNotNull(access.getInteraction(tenantId, interaction.id))
        assertEquals(interaction.contactId, stored.contactId)
        assertEquals(interaction.userId, stored.userId)
        assertEquals(InteractionChannel.IN_PERSON, stored.channel)
        assertEquals("Met at the conference", stored.notes)
        assertEquals(Instant.parse("2026-08-22T10:00:00Z"), stored.occurredAt)
    }

    @Test
    fun `does not expose mutate or delete interactions from another tenant`() = runBlocking {
        val tenantId = randomUuid()
        val otherTenantId = randomUuid()
        val interaction = interaction(randomUuid(), Instant.parse("2026-08-20T10:00:00Z"), InteractionChannel.CHAT, null)
        access.registerInteraction(tenantId, interaction)

        assertNull(access.getInteraction(otherTenantId, interaction.id))
        assertEquals(emptyList(), access.getContactInteractions(otherTenantId, interaction.contactId))
        assertFalse(access.updateInteraction(otherTenantId, interaction.copy(notes = "Impostor update")))
        assertFalse(access.deleteInteraction(otherTenantId, interaction.id))
        assertEquals(interaction.notes, access.getInteraction(tenantId, interaction.id)?.notes)

        assertEquals(true, access.deleteInteraction(tenantId, interaction.id))
        assertNull(access.getInteraction(tenantId, interaction.id))
    }

    private fun interaction(
        contactId: Uuid,
        occurredAt: Instant,
        channel: InteractionChannel,
        notes: String?,
    ): Interaction = Interaction(
        id = randomUuid(),
        contactId = contactId,
        userId = randomUuid(),
        channel = channel,
        notes = notes,
        occurredAt = occurredAt,
    )
}

private object EngagementTestDatabase {
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

@OptIn(ExperimentalUuidApi::class)
private fun randomUuid(): Uuid = Uuid.generateV7()
