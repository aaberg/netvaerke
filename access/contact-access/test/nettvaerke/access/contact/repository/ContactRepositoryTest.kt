package nettvaerke.access.contact.repository

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import nettvaerke.access.contact.*
import netvaerke.testsupport.PostgresTestDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ContactRepositoryTest {
    private val dataSource: DataSource
        get() = ContactTestDatabase.dataSource

    private val access: ContactAccess
        get() = ContactAccessImpl(ContactRepository(dataSource))

    @BeforeTest
    fun clearContacts() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("TRUNCATE TABLE contact.contact")
            }
        }
    }

    @Test
    fun `round trips all contact detail types through jsonb`() {
        val contact = Contact(
            id = randomUuid(),
            name = "Ada Lovelace",
            tenantId = randomUuid(),
            contactDetails = listOf(
                EmailAddress(
                    "ada@example.com",
                    isPrimary = true,
                    label = "Work"
                ),
                PhoneNumber("+45 12 34 56 78", label = "Mobile"),
                Note("Prefers email"),
                WorkInfo(
                    title = "Mathematician",
                    organization = "Analytical Engines Ltd."
                ),
                ContactImage(
                    fileKey = "contacts/ada.png",
                    mimeType = "image/png"
                ),
            ),
        )

        access.saveContact(contact)

        assertEquals(contact, access.getContact(contact.id))
        assertEquals(listOf(contact), access.getContacts(contact.tenantId))
        assertEquals("array" to 5, storedDetailsShape(contact.id))
    }

    @Test
    fun `updates filters and deletes contacts`() {
        val tenantId = randomUuid()
        val otherTenantId = randomUuid()
        val contact = Contact(
            id = randomUuid(),
            name = "Grace Hopper",
            tenantId = tenantId,
            contactDetails = listOf(
                EmailAddress(
                    "grace@example.com",
                    isPrimary = true
                )
            ),
        )
        val otherContact = Contact(
            id = randomUuid(),
            name = "Katherine Johnson",
            tenantId = otherTenantId,
            contactDetails = emptyList(),
        )
        access.saveContact(contact)
        access.saveContact(otherContact)

        assertEquals(listOf(contact), access.getContacts(tenantId))
        assertEquals(listOf(otherContact), access.getContacts(otherTenantId))

        val updated = contact.copy(
            name = "Rear Admiral Grace Hopper",
            contactDetails = listOf(Note("COBOL pioneer")),
        )
        access.saveContact(updated)
        assertEquals(updated, access.getContact(contact.id))

        access.deleteContact(updated.copy(tenantId = otherTenantId))
        assertEquals(updated, access.getContact(contact.id))

        access.deleteContact(updated)
        assertNull(access.getContact(contact.id))
    }

    private fun storedDetailsShape(id: Uuid): Pair<String, Int> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT jsonb_typeof(details), jsonb_array_length(details) FROM contact.contact WHERE id = ?",
            ).use { statement ->
                statement.setObject(1, UUID.fromString(id.toString()))
                statement.executeQuery().use { result ->
                    check(result.next())
                    result.getString(1) to result.getInt(2)
                }
            }
        }
}

private object ContactTestDatabase {
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
