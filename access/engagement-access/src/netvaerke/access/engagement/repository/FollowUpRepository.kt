package netvaerke.access.engagement.repository

import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import netvaerke.access.engagement.CancelFollowUpResult
import netvaerke.access.engagement.ChangeFollowUpCadenceResult
import netvaerke.access.engagement.CompleteFollowUpResult
import netvaerke.access.engagement.FollowUp
import netvaerke.access.engagement.FollowUpCadence
import netvaerke.access.engagement.FollowUpCompletion
import netvaerke.access.engagement.FollowUpIntervalUnit
import netvaerke.access.engagement.FollowUpSchedule
import netvaerke.access.engagement.FollowUpStatus
import netvaerke.access.engagement.RegisterFollowUp
import netvaerke.access.engagement.RegisterFollowUpResult
import netvaerke.access.engagement.RescheduleFollowUpResult

class FollowUpRepository(
    private val dataSource: DataSource,
) {
    fun registerFollowUp(tenantId: Uuid, registration: RegisterFollowUp): RegisterFollowUpResult =
        inTransaction { connection ->
            when (val schedule = registration.schedule) {
                FollowUpSchedule.OneTime -> {
                    val stored = insertFollowUp(
                        connection = connection,
                        tenantId = tenantId,
                        id = registration.id,
                        resourceId = registration.resourceId,
                        ruleId = null,
                        dueOn = registration.dueOn,
                        cadence = null,
                    ) ?: return@inTransaction RegisterFollowUpResult.IdAlreadyExists
                    RegisterFollowUpResult.Registered(stored.followUp)
                }

                is FollowUpSchedule.Recurring -> {
                    val ruleId = newId()
                    if (!insertRule(connection, tenantId, ruleId, registration.resourceId, schedule.cadence)) {
                        return@inTransaction RegisterFollowUpResult.ActiveRecurrenceAlreadyExists
                    }
                    val stored = insertFollowUp(
                        connection = connection,
                        tenantId = tenantId,
                        id = registration.id,
                        resourceId = registration.resourceId,
                        ruleId = ruleId,
                        dueOn = registration.dueOn,
                        cadence = schedule.cadence,
                    )
                    if (stored == null) {
                        deleteRule(connection, ruleId)
                        RegisterFollowUpResult.IdAlreadyExists
                    } else {
                        RegisterFollowUpResult.Registered(stored.followUp)
                    }
                }
            }
        }

    fun getFollowUp(tenantId: Uuid, followUpId: Uuid): FollowUp? =
        dataSource.connection.use { connection ->
            findFollowUp(connection, tenantId, followUpId, forUpdate = false)?.followUp
        }

    fun getResourceFollowUps(tenantId: Uuid, resourceId: Uuid): List<FollowUp> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_RESOURCE_FOLLOW_UPS).use { statement ->
                statement.setObject(1, tenantId.toJavaUuid())
                statement.setObject(2, resourceId.toJavaUuid())
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toStoredFollowUp().followUp)
                    }
                }
            }
        }

    fun getOpenFollowUpsDueBy(tenantId: Uuid, dueOn: LocalDate): List<FollowUp> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_OPEN_FOLLOW_UPS_DUE_BY).use { statement ->
                statement.setObject(1, tenantId.toJavaUuid())
                statement.setObject(2, dueOn)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toStoredFollowUp().followUp)
                    }
                }
            }
        }

    fun rescheduleFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        dueOn: LocalDate,
    ): RescheduleFollowUpResult = inTransaction { connection ->
        val stored = findFollowUp(connection, tenantId, followUpId, forUpdate = true)
            ?: return@inTransaction RescheduleFollowUpResult.NotFound
        if (stored.followUp.status != FollowUpStatus.OPEN) {
            return@inTransaction RescheduleFollowUpResult.NotOpen
        }
        if (stored.followUp.dueOn != dueOn) {
            connection.prepareStatement(RESCHEDULE_FOLLOW_UP).use { statement ->
                statement.setObject(1, dueOn)
                statement.setObject(2, followUpId.toJavaUuid())
                statement.setObject(3, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
        }
        RescheduleFollowUpResult.Rescheduled(
            checkNotNull(findFollowUp(connection, tenantId, followUpId, forUpdate = false)).followUp,
        )
    }

    fun changeFollowUpCadence(
        tenantId: Uuid,
        followUpId: Uuid,
        cadence: FollowUpCadence,
    ): ChangeFollowUpCadenceResult = inTransaction { connection ->
        val stored = findFollowUp(connection, tenantId, followUpId, forUpdate = true)
            ?: return@inTransaction ChangeFollowUpCadenceResult.NotFound
        if (stored.followUp.status != FollowUpStatus.OPEN) {
            return@inTransaction ChangeFollowUpCadenceResult.NotOpen
        }
        val ruleId = stored.ruleId ?: return@inTransaction ChangeFollowUpCadenceResult.NotRecurring
        val rule = findActiveRule(connection, tenantId, ruleId)
            ?: return@inTransaction ChangeFollowUpCadenceResult.NotRecurring
        val dueOn = rule.lastCompletedOn?.plus(cadence) ?: stored.followUp.dueOn

        connection.prepareStatement(UPDATE_RULE_CADENCE).use { statement ->
            statement.setInt(1, cadence.amount)
            statement.setString(2, cadence.unit.name)
            statement.setObject(3, ruleId.toJavaUuid())
            statement.setObject(4, tenantId.toJavaUuid())
            check(statement.executeUpdate() == 1)
        }
        connection.prepareStatement(UPDATE_FOLLOW_UP_SCHEDULE).use { statement ->
            statement.setInt(1, cadence.amount)
            statement.setString(2, cadence.unit.name)
            statement.setObject(3, dueOn)
            statement.setObject(4, dueOn)
            statement.setObject(5, followUpId.toJavaUuid())
            statement.setObject(6, tenantId.toJavaUuid())
            check(statement.executeUpdate() == 1)
        }
        ChangeFollowUpCadenceResult.Changed(
            checkNotNull(findFollowUp(connection, tenantId, followUpId, forUpdate = false)).followUp,
        )
    }

    fun completeFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        completedOn: LocalDate,
    ): CompleteFollowUpResult = inTransaction { connection ->
        val stored = findFollowUp(connection, tenantId, followUpId, forUpdate = true)
            ?: return@inTransaction CompleteFollowUpResult.NotFound
        when (stored.followUp.status) {
            FollowUpStatus.DONE -> return@inTransaction CompleteFollowUpResult.AlreadyDone
            FollowUpStatus.CANCELLED -> return@inTransaction CompleteFollowUpResult.Cancelled
            FollowUpStatus.OPEN -> Unit
        }

        val completed = markCompleted(connection, tenantId, followUpId, completedOn)
        val next = stored.ruleId?.let { ruleId ->
            val rule = checkNotNull(findActiveRule(connection, tenantId, ruleId)) {
                "Open recurring follow-up has no active rule"
            }
            connection.prepareStatement(RECORD_RULE_COMPLETION).use { statement ->
                statement.setObject(1, completedOn)
                statement.setObject(2, ruleId.toJavaUuid())
                statement.setObject(3, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
            insertSuccessor(
                connection = connection,
                tenantId = tenantId,
                resourceId = stored.followUp.resourceId,
                ruleId = ruleId,
                dueOn = completedOn.plus(rule.cadence),
                cadence = rule.cadence,
            ).followUp
        }
        CompleteFollowUpResult.Completed(FollowUpCompletion(completed.followUp, next))
    }

    fun cancelFollowUp(tenantId: Uuid, followUpId: Uuid): CancelFollowUpResult =
        inTransaction { connection ->
            val stored = findFollowUp(connection, tenantId, followUpId, forUpdate = true)
                ?: return@inTransaction CancelFollowUpResult.NotFound
            when (stored.followUp.status) {
                FollowUpStatus.DONE -> return@inTransaction CancelFollowUpResult.AlreadyDone
                FollowUpStatus.CANCELLED -> return@inTransaction CancelFollowUpResult.AlreadyCancelled
                FollowUpStatus.OPEN -> Unit
            }

            connection.prepareStatement(CANCEL_FOLLOW_UP).use { statement ->
                statement.setObject(1, followUpId.toJavaUuid())
                statement.setObject(2, tenantId.toJavaUuid())
                check(statement.executeUpdate() == 1)
            }
            stored.ruleId?.let { ruleId ->
                connection.prepareStatement(RETIRE_RULE).use { statement ->
                    statement.setObject(1, ruleId.toJavaUuid())
                    statement.setObject(2, tenantId.toJavaUuid())
                    check(statement.executeUpdate() == 1)
                }
            }
            CancelFollowUpResult.Cancelled(
                checkNotNull(findFollowUp(connection, tenantId, followUpId, forUpdate = false)).followUp,
            )
        }

    private fun insertRule(
        connection: Connection,
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

    private fun deleteRule(connection: Connection, ruleId: Uuid) {
        connection.prepareStatement(DELETE_RULE).use { statement ->
            statement.setObject(1, ruleId.toJavaUuid())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertFollowUp(
        connection: Connection,
        tenantId: Uuid,
        id: Uuid,
        resourceId: Uuid,
        ruleId: Uuid?,
        dueOn: LocalDate,
        cadence: FollowUpCadence?,
    ): StoredFollowUp? = connection.prepareStatement(INSERT_FOLLOW_UP).use { statement ->
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
            if (result.next()) result.toStoredFollowUp() else null
        }
    }

    private fun insertSuccessor(
        connection: Connection,
        tenantId: Uuid,
        resourceId: Uuid,
        ruleId: Uuid,
        dueOn: LocalDate,
        cadence: FollowUpCadence,
    ): StoredFollowUp {
        repeat(ID_GENERATION_ATTEMPTS) {
            insertFollowUp(connection, tenantId, newId(), resourceId, ruleId, dueOn, cadence)?.let { return it }
        }
        error("Could not generate a unique follow-up ID")
    }

    private fun markCompleted(
        connection: Connection,
        tenantId: Uuid,
        followUpId: Uuid,
        completedOn: LocalDate,
    ): StoredFollowUp = connection.prepareStatement(COMPLETE_FOLLOW_UP).use { statement ->
        statement.setObject(1, completedOn)
        statement.setObject(2, followUpId.toJavaUuid())
        statement.setObject(3, tenantId.toJavaUuid())
        statement.executeQuery().use { result ->
            check(result.next())
            result.toStoredFollowUp()
        }
    }

    private fun findFollowUp(
        connection: Connection,
        tenantId: Uuid,
        followUpId: Uuid,
        forUpdate: Boolean,
    ): StoredFollowUp? {
        val sql = if (forUpdate) GET_FOLLOW_UP_FOR_UPDATE else GET_FOLLOW_UP
        return connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, followUpId.toJavaUuid())
            statement.setObject(2, tenantId.toJavaUuid())
            statement.executeQuery().use { result ->
                if (result.next()) result.toStoredFollowUp() else null
            }
        }
    }

    private fun findActiveRule(connection: Connection, tenantId: Uuid, ruleId: Uuid): StoredRule? =
        connection.prepareStatement(GET_ACTIVE_RULE_FOR_UPDATE).use { statement ->
            statement.setObject(1, ruleId.toJavaUuid())
            statement.setObject(2, tenantId.toJavaUuid())
            statement.executeQuery().use { result ->
                if (result.next()) {
                    StoredRule(
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

    private fun ResultSet.toStoredFollowUp(): StoredFollowUp {
        val ruleId = getString("rule_id")?.let(Uuid::parse)
        val cadenceAmount = getInt("cadence_amount").takeUnless { wasNull() }
        val cadenceUnit = getString("cadence_unit")?.let(FollowUpIntervalUnit::valueOf)
        val schedule = if (ruleId == null) {
            FollowUpSchedule.OneTime
        } else {
            FollowUpSchedule.Recurring(
                FollowUpCadence(
                    amount = checkNotNull(cadenceAmount),
                    unit = checkNotNull(cadenceUnit),
                ),
            )
        }
        return StoredFollowUp(
            followUp = FollowUp(
                id = Uuid.parse(getString("id")),
                resourceId = Uuid.parse(getString("resource_id")),
                dueOn = getObject("due_on", LocalDate::class.java),
                dueDateRevision = getLong("due_date_revision"),
                schedule = schedule,
                status = FollowUpStatus.valueOf(getString("status")),
                completedOn = getObject("completed_on", LocalDate::class.java),
                createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
            ),
            ruleId = ruleId,
        )
    }

    private fun LocalDate.plus(cadence: FollowUpCadence): LocalDate = when (cadence.unit) {
        FollowUpIntervalUnit.DAYS -> plusDays(cadence.amount.toLong())
        FollowUpIntervalUnit.WEEKS -> plusWeeks(cadence.amount.toLong())
        FollowUpIntervalUnit.MONTHS -> plusMonths(cadence.amount.toLong())
        FollowUpIntervalUnit.YEARS -> plusYears(cadence.amount.toLong())
    }

    private inline fun <T> inTransaction(block: (Connection) -> T): T =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                block(connection).also { connection.commit() }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }

    private fun Uuid.toJavaUuid(): UUID = UUID.fromString(toString())

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): Uuid = Uuid.generateV7()

    private data class StoredFollowUp(
        val followUp: FollowUp,
        val ruleId: Uuid?,
    )

    private data class StoredRule(
        val cadence: FollowUpCadence,
        val lastCompletedOn: LocalDate?,
    )

    private companion object {
        const val ID_GENERATION_ATTEMPTS = 3

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

        const val RESCHEDULE_FOLLOW_UP = """
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
