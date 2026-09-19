package netvaerke.access.engagement.repository

import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.uuid.Uuid
import netvaerke.access.engagement.FollowUpCadence
import netvaerke.access.engagement.FollowUpIntervalUnit
import netvaerke.access.engagement.FollowUpStatus

class FollowUpRepository(
    private val dataSource: DataSource,
) {
    internal fun getFollowUp(tenantId: Uuid, followUpId: Uuid): FollowUpEntity? =
        dataSource.connection.use { connection ->
            findFollowUp(connection, tenantId, followUpId, forUpdate = false)
        }

    internal fun getResourceFollowUps(tenantId: Uuid, resourceId: Uuid): List<FollowUpEntity> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_RESOURCE_FOLLOW_UPS).use { statement ->
                statement.setObject(1, tenantId.toJavaUuid())
                statement.setObject(2, resourceId.toJavaUuid())
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toFollowUpEntity())
                    }
                }
            }
        }

    internal fun getOpenFollowUpsDueBy(tenantId: Uuid, dueOn: LocalDate): List<FollowUpEntity> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_OPEN_FOLLOW_UPS_DUE_BY).use { statement ->
                statement.setObject(1, tenantId.toJavaUuid())
                statement.setObject(2, dueOn)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toFollowUpEntity())
                    }
                }
            }
        }

    internal fun <T> inTransaction(block: Transaction.() -> T): T =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                block(Transaction(connection)).also { connection.commit() }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }

    internal inner class Transaction internal constructor(
        internal val connection: Connection,
    ) {
        fun getFollowUp(tenantId: Uuid, followUpId: Uuid, forUpdate: Boolean = false): FollowUpEntity? =
            findFollowUp(connection, tenantId, followUpId, forUpdate)

        fun insertRule(
            tenantId: Uuid,
            ruleId: Uuid,
            resourceId: Uuid,
            cadence: FollowUpCadence,
        ): Boolean = connection.prepareStatement(INSERT_RULE).use { statement ->
            statement.setObject(1, ruleId.toJavaUuid())
            statement.setObject(2, tenantId.toJavaUuid())
            statement.setObject(3, resourceId.toJavaUuid())
            statement.setInt(4, cadence.amount)
            statement.setString(5, cadence.unit.name)
            statement.executeUpdate() == 1
        }

        fun deleteRule(ruleId: Uuid) {
            connection.prepareStatement(DELETE_RULE).use { statement ->
                statement.setObject(1, ruleId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
        }

        fun insertFollowUp(
            tenantId: Uuid,
            id: Uuid,
            resourceId: Uuid,
            ruleId: Uuid?,
            dueOn: LocalDate,
            cadence: FollowUpCadence?,
        ): FollowUpEntity? = connection.prepareStatement(INSERT_FOLLOW_UP).use { statement ->
            statement.setObject(1, id.toJavaUuid())
            statement.setObject(2, tenantId.toJavaUuid())
            statement.setObject(3, resourceId.toJavaUuid())
            statement.setObject(4, ruleId?.toJavaUuid())
            statement.setObject(5, dueOn)
            if (cadence == null) {
                statement.setObject(6, null)
                statement.setObject(7, null)
            } else {
                statement.setInt(6, cadence.amount)
                statement.setString(7, cadence.unit.name)
            }
            statement.executeQuery().use { result ->
                if (result.next()) result.toFollowUpEntity() else null
            }
        }

        fun updateDueDate(tenantId: Uuid, followUpId: Uuid, dueOn: LocalDate) {
            connection.prepareStatement(UPDATE_DUE_DATE).use { statement ->
                statement.setObject(1, dueOn)
                statement.setObject(2, followUpId.toJavaUuid())
                statement.setObject(3, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
        }

        fun getActiveRule(tenantId: Uuid, ruleId: Uuid): FollowUpRuleEntity? =
            connection.prepareStatement(GET_ACTIVE_RULE_FOR_UPDATE).use { statement ->
                statement.setObject(1, ruleId.toJavaUuid())
                statement.setObject(2, tenantId.toJavaUuid())
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        FollowUpRuleEntity(
                            cadence = FollowUpCadence(
                                amount = result.getInt("cadence_amount"),
                                unit = FollowUpIntervalUnit.valueOf(result.getString("cadence_unit")),
                            ),
                            lastCompletedOn = result.getObject("last_completed_on", LocalDate::class.java),
                        )
                    } else {
                        null
                    }
                }
            }

        fun updateRuleCadence(tenantId: Uuid, ruleId: Uuid, cadence: FollowUpCadence) {
            connection.prepareStatement(UPDATE_RULE_CADENCE).use { statement ->
                statement.setInt(1, cadence.amount)
                statement.setString(2, cadence.unit.name)
                statement.setObject(3, ruleId.toJavaUuid())
                statement.setObject(4, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
        }

        fun updateFollowUpSchedule(
            tenantId: Uuid,
            followUpId: Uuid,
            cadence: FollowUpCadence,
            dueOn: LocalDate,
        ) {
            connection.prepareStatement(UPDATE_FOLLOW_UP_SCHEDULE).use { statement ->
                statement.setInt(1, cadence.amount)
                statement.setString(2, cadence.unit.name)
                statement.setObject(3, dueOn)
                statement.setObject(4, dueOn)
                statement.setObject(5, followUpId.toJavaUuid())
                statement.setObject(6, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
        }

        fun updateCompletion(
            tenantId: Uuid,
            followUpId: Uuid,
            completedOn: LocalDate,
        ): FollowUpEntity = connection.prepareStatement(COMPLETE_FOLLOW_UP).use { statement ->
            statement.setObject(1, completedOn)
            statement.setObject(2, followUpId.toJavaUuid())
            statement.setObject(3, tenantId.toJavaUuid())
            statement.executeQuery().use { result ->
                check(result.next())
                result.toFollowUpEntity()
            }
        }

        fun updateRuleLastCompletion(tenantId: Uuid, ruleId: Uuid, completedOn: LocalDate) {
            connection.prepareStatement(RECORD_RULE_COMPLETION).use { statement ->
                statement.setObject(1, completedOn)
                statement.setObject(2, ruleId.toJavaUuid())
                statement.setObject(3, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
        }

        fun updateStatusCancelled(tenantId: Uuid, followUpId: Uuid) {
            connection.prepareStatement(CANCEL_FOLLOW_UP).use { statement ->
                statement.setObject(1, followUpId.toJavaUuid())
                statement.setObject(2, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
        }

        fun retireRule(tenantId: Uuid, ruleId: Uuid) {
            connection.prepareStatement(RETIRE_RULE).use { statement ->
                statement.setObject(1, ruleId.toJavaUuid())
                statement.setObject(2, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
        }
    }

    private fun findFollowUp(
        connection: Connection,
        tenantId: Uuid,
        followUpId: Uuid,
        forUpdate: Boolean,
    ): FollowUpEntity? {
        val sql = if (forUpdate) GET_FOLLOW_UP_FOR_UPDATE else GET_FOLLOW_UP
        return connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, followUpId.toJavaUuid())
            statement.setObject(2, tenantId.toJavaUuid())
            statement.executeQuery().use { result ->
                if (result.next()) result.toFollowUpEntity() else null
            }
        }
    }

    private fun ResultSet.toFollowUpEntity(): FollowUpEntity = FollowUpEntity(
        id = Uuid.parse(getString("id")),
        resourceId = Uuid.parse(getString("resource_id")),
        ruleId = getString("rule_id")?.let(Uuid::parse),
        dueOn = getObject("due_on", LocalDate::class.java),
        dueDateRevision = getLong("due_date_revision"),
        cadence = getInt("cadence_amount").takeUnless { wasNull() }?.let { amount ->
            FollowUpCadence(
                amount = amount,
                unit = FollowUpIntervalUnit.valueOf(getString("cadence_unit")),
            )
        },
        status = FollowUpStatus.valueOf(getString("status")),
        completedOn = getObject("completed_on", LocalDate::class.java),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
    )

    private fun Uuid.toJavaUuid(): UUID = UUID.fromString(toString())

    private companion object {
        const val FOLLOW_UP_COLUMNS = """
            id, resource_id, rule_id, due_on, due_date_revision, status,
            cadence_amount, cadence_unit, completed_on, created_at
        """

        const val INSERT_RULE = """
            INSERT INTO engagement.follow_up_rule (
                id, tenant, resource_id, cadence_amount, cadence_unit
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (tenant, resource_id) WHERE retired_at IS NULL DO NOTHING
        """

        const val DELETE_RULE = """
            DELETE FROM engagement.follow_up_rule WHERE id = ?
        """

        const val INSERT_FOLLOW_UP = """
            INSERT INTO engagement.follow_up (
                id, tenant, resource_id, rule_id, due_on, status, cadence_amount, cadence_unit
            ) VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?)
            ON CONFLICT (id) DO NOTHING
            RETURNING $FOLLOW_UP_COLUMNS
        """

        const val GET_FOLLOW_UP = """
            SELECT $FOLLOW_UP_COLUMNS
            FROM engagement.follow_up
            WHERE id = ? AND tenant = ?
        """

        const val GET_FOLLOW_UP_FOR_UPDATE = """
            SELECT $FOLLOW_UP_COLUMNS
            FROM engagement.follow_up
            WHERE id = ? AND tenant = ?
            FOR UPDATE
        """

        const val GET_RESOURCE_FOLLOW_UPS = """
            SELECT $FOLLOW_UP_COLUMNS
            FROM engagement.follow_up
            WHERE tenant = ? AND resource_id = ?
            ORDER BY due_on DESC, id DESC
        """

        const val GET_OPEN_FOLLOW_UPS_DUE_BY = """
            SELECT $FOLLOW_UP_COLUMNS
            FROM engagement.follow_up
            WHERE tenant = ? AND status = 'OPEN' AND due_on <= ?
            ORDER BY due_on ASC, id ASC
        """

        const val UPDATE_DUE_DATE = """
            UPDATE engagement.follow_up
            SET due_on = ?, due_date_revision = due_date_revision + 1
            WHERE id = ? AND tenant = ?
        """

        const val GET_ACTIVE_RULE_FOR_UPDATE = """
            SELECT cadence_amount, cadence_unit, last_completed_on
            FROM engagement.follow_up_rule
            WHERE id = ? AND tenant = ? AND retired_at IS NULL
            FOR UPDATE
        """

        const val UPDATE_RULE_CADENCE = """
            UPDATE engagement.follow_up_rule
            SET cadence_amount = ?, cadence_unit = ?
            WHERE id = ? AND tenant = ? AND retired_at IS NULL
        """

        const val UPDATE_FOLLOW_UP_SCHEDULE = """
            UPDATE engagement.follow_up
            SET cadence_amount = ?,
                cadence_unit = ?,
                due_on = ?,
                due_date_revision = due_date_revision + CASE WHEN due_on = ? THEN 0 ELSE 1 END
            WHERE id = ? AND tenant = ?
        """

        const val COMPLETE_FOLLOW_UP = """
            UPDATE engagement.follow_up
            SET status = 'DONE', completed_on = ?
            WHERE id = ? AND tenant = ?
            RETURNING $FOLLOW_UP_COLUMNS
        """

        const val RECORD_RULE_COMPLETION = """
            UPDATE engagement.follow_up_rule
            SET last_completed_on = ?
            WHERE id = ? AND tenant = ? AND retired_at IS NULL
        """

        const val CANCEL_FOLLOW_UP = """
            UPDATE engagement.follow_up
            SET status = 'CANCELLED'
            WHERE id = ? AND tenant = ?
        """

        const val RETIRE_RULE = """
            UPDATE engagement.follow_up_rule
            SET retired_at = NOW()
            WHERE id = ? AND tenant = ? AND retired_at IS NULL
        """
    }
}
