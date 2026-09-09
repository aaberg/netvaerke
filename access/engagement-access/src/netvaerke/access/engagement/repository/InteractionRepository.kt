package netvaerke.access.engagement.repository

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlin.uuid.Uuid
import netvaerke.access.engagement.InteractionChannel

class InteractionRepository(
    private val dataSource: DataSource,
) {
    fun registerInteraction(interaction: InteractionEntity): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(REGISTER_INTERACTION).use { statement ->
                statement.setObject(1, interaction.id.toJavaUuid())
                statement.setObject(2, interaction.tenantId.toJavaUuid())
                statement.setObject(3, interaction.resourceId.toJavaUuid())
                statement.setObject(4, interaction.userId.toJavaUuid())
                statement.setString(5, interaction.channel.name)
                statement.setString(6, interaction.notes)
                statement.setObject(7, interaction.occurredAt.atOffset(ZoneOffset.UTC))
                statement.executeUpdate() == 1
            }
        }

    fun updateInteraction(interaction: InteractionEntity): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(UPDATE_INTERACTION).use { statement ->
                statement.setString(1, interaction.channel.name)
                statement.setString(2, interaction.notes)
                statement.setObject(3, interaction.occurredAt.atOffset(ZoneOffset.UTC))
                statement.setObject(4, interaction.id.toJavaUuid())
                statement.setObject(5, interaction.tenantId.toJavaUuid())
                statement.executeUpdate() == 1
            }
        }

    fun deleteInteraction(tenantId: Uuid, interactionId: Uuid): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(DELETE_INTERACTION).use { statement ->
                statement.setObject(1, interactionId.toJavaUuid())
                statement.setObject(2, tenantId.toJavaUuid())
                statement.executeUpdate() == 1
            }
        }

    fun getInteraction(tenantId: Uuid, interactionId: Uuid): InteractionEntity? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_INTERACTION).use { statement ->
                statement.setObject(1, interactionId.toJavaUuid())
                statement.setObject(2, tenantId.toJavaUuid())
                statement.executeQuery().use { result ->
                    if (result.next()) result.toInteractionEntity() else null
                }
            }
        }

    fun getResourceInteractions(tenantId: Uuid, resourceId: Uuid): List<InteractionEntity> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_RESOURCE_INTERACTIONS).use { statement ->
                statement.setObject(1, tenantId.toJavaUuid())
                statement.setObject(2, resourceId.toJavaUuid())
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.toInteractionEntity())
                        }
                    }
                }
            }
        }

    private fun ResultSet.toInteractionEntity(): InteractionEntity = InteractionEntity(
        id = Uuid.parse(getString("id")),
        tenantId = Uuid.parse(getString("tenant")),
        resourceId = Uuid.parse(getString("resource_id")),
        userId = Uuid.parse(getString("user_id")),
        channel = InteractionChannel.valueOf(getString("channel")),
        notes = getString("notes"),
        occurredAt = getObject("occurred_at", OffsetDateTime::class.java).toInstant(),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
    )

    private fun Uuid.toJavaUuid(): UUID = UUID.fromString(toString())

    private companion object {
        const val REGISTER_INTERACTION = """
            INSERT INTO engagement.interaction (
                id, tenant, resource_id, user_id, channel, notes, occurred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
        """

        const val UPDATE_INTERACTION = """
            UPDATE engagement.interaction
            SET channel = ?, notes = ?, occurred_at = ?
            WHERE id = ? AND tenant = ?
        """

        const val DELETE_INTERACTION = """
            DELETE FROM engagement.interaction
            WHERE id = ? AND tenant = ?
        """

        const val GET_INTERACTION = """
            SELECT id, tenant, resource_id, user_id, channel, notes, occurred_at, created_at
            FROM engagement.interaction
            WHERE id = ? AND tenant = ?
        """

        const val GET_RESOURCE_INTERACTIONS = """
            SELECT id, tenant, resource_id, user_id, channel, notes, occurred_at, created_at
            FROM engagement.interaction
            WHERE tenant = ? AND resource_id = ?
            ORDER BY occurred_at DESC, id DESC
        """
    }
}
