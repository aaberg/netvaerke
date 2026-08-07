package nettvaerke.access.contact.repository

import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*
import javax.sql.DataSource
import kotlin.uuid.Uuid

class ContactRepository(
    private val dataSource: DataSource,
    private val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    },
) {
    fun saveContact(contact: ContactEntity) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(SAVE_CONTACT).use { statement ->
                statement.setObject(1, contact.id.toJavaUuid())
                statement.setString(2, contact.name)
                statement.setObject(3, contact.tenantId.toJavaUuid())
                statement.setString(4, json.encodeToString(contact.details))
                statement.executeUpdate()
            }
        }
    }

    fun deleteContact(contact: ContactEntity) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(DELETE_CONTACT).use { statement ->
                statement.setObject(1, contact.id.toJavaUuid())
                statement.setObject(2, contact.tenantId.toJavaUuid())
                statement.executeUpdate()
            }
        }
    }

    fun getContact(id: Uuid): ContactEntity? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_CONTACT).use { statement ->
                statement.setObject(1, id.toJavaUuid())
                statement.executeQuery().use { result ->
                    if (result.next()) result.toContactEntity() else null
                }
            }
        }

    fun getContacts(tenantId: Uuid): List<ContactEntity> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_CONTACTS).use { statement ->
                statement.setObject(1, tenantId.toJavaUuid())
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.toContactEntity())
                        }
                    }
                }
            }
        }

    private fun ResultSet.toContactEntity(): ContactEntity = ContactEntity(
        id = Uuid.parse(getString("id")),
        name = getString("name"),
        tenantId = Uuid.parse(getString("tenant")),
        details = getString("details")?.let(json::decodeFromString) ?: emptyList(),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
    )

    private fun Uuid.toJavaUuid(): UUID = UUID.fromString(toString())

    private companion object {
        const val SAVE_CONTACT = """
            INSERT INTO contact.contact (id, name, tenant, details)
            VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                tenant = EXCLUDED.tenant,
                details = EXCLUDED.details
        """

        const val DELETE_CONTACT = """
            DELETE FROM contact.contact
            WHERE id = ? AND tenant = ?
        """

        const val GET_CONTACT = """
            SELECT id, name, tenant, details, created_at
            FROM contact.contact
            WHERE id = ?
        """

        const val GET_CONTACTS = """
            SELECT id, name, tenant, details, created_at
            FROM contact.contact
            WHERE tenant = ?
            ORDER BY created_at, id
        """
    }
}
