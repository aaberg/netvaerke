package netvaerke.access.engagement

import java.time.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import netvaerke.access.engagement.repository.FollowUpEntity
import netvaerke.access.engagement.repository.FollowUpRepository
import netvaerke.access.engagement.repository.InteractionEntity
import netvaerke.access.engagement.repository.InteractionRepository

class EngagementAccessImpl(
    private val interactionRepository: InteractionRepository,
    private val followUpRepository: FollowUpRepository,
    private val jdbcDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EngagementAccess {
    override suspend fun registerInteraction(tenantId: Uuid, interaction: Interaction): Boolean =
        withContext(jdbcDispatcher) {
            interactionRepository.registerInteraction(interaction.toEntity(tenantId))
        }

    override suspend fun updateInteraction(tenantId: Uuid, interaction: Interaction): Boolean =
        withContext(jdbcDispatcher) {
            interactionRepository.updateInteraction(interaction.toEntity(tenantId))
        }

    override suspend fun deleteInteraction(tenantId: Uuid, interactionId: Uuid): Boolean =
        withContext(jdbcDispatcher) {
            interactionRepository.deleteInteraction(tenantId, interactionId)
        }

    override suspend fun getInteraction(tenantId: Uuid, interactionId: Uuid): Interaction? =
        withContext(jdbcDispatcher) {
            interactionRepository.getInteraction(tenantId, interactionId)
        }?.toInteraction()

    override suspend fun getResourceInteractions(tenantId: Uuid, resourceId: Uuid): List<Interaction> =
        withContext(jdbcDispatcher) {
            interactionRepository.getResourceInteractions(tenantId, resourceId)
        }.map { it.toInteraction() }

    override suspend fun registerFollowUp(
        tenantId: Uuid,
        followUp: RegisterFollowUp,
    ): RegisterFollowUpResult = withContext(jdbcDispatcher) {
        followUpRepository.inTransaction {
            when (val schedule = followUp.schedule) {
                FollowUpSchedule.OneTime -> registerOneTimeFollowUp(tenantId, followUp)
                is FollowUpSchedule.Recurring -> registerRecurringFollowUp(tenantId, followUp, schedule.cadence)
            }
        }
    }

    override suspend fun getFollowUp(tenantId: Uuid, followUpId: Uuid): FollowUp? =
        withContext(jdbcDispatcher) {
            followUpRepository.getFollowUp(tenantId, followUpId)
        }?.toFollowUp()

    override suspend fun getResourceFollowUps(tenantId: Uuid, resourceId: Uuid): List<FollowUp> =
        withContext(jdbcDispatcher) {
            followUpRepository.getResourceFollowUps(tenantId, resourceId)
        }.map { it.toFollowUp() }

    override suspend fun getOpenFollowUpsDueBy(tenantId: Uuid, dueOn: LocalDate): List<FollowUp> =
        withContext(jdbcDispatcher) {
            followUpRepository.getOpenFollowUpsDueBy(tenantId, dueOn)
        }.map { it.toFollowUp() }

    override suspend fun rescheduleFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        dueOn: LocalDate,
    ): RescheduleFollowUpResult = withContext(jdbcDispatcher) {
        followUpRepository.inTransaction {
            val stored = getFollowUp(tenantId, followUpId, forUpdate = true)
                ?: return@inTransaction RescheduleFollowUpResult.NotFound
            if (stored.status != FollowUpStatus.OPEN) {
                return@inTransaction RescheduleFollowUpResult.NotOpen
            }
            if (stored.dueOn != dueOn) {
                updateDueDate(tenantId, followUpId, dueOn)
            }
            RescheduleFollowUpResult.Rescheduled(
                checkNotNull(getFollowUp(tenantId, followUpId)).toFollowUp(),
            )
        }
    }

    override suspend fun changeFollowUpCadence(
        tenantId: Uuid,
        followUpId: Uuid,
        cadence: FollowUpCadence,
    ): ChangeFollowUpCadenceResult = withContext(jdbcDispatcher) {
        followUpRepository.inTransaction {
            val stored = getFollowUp(tenantId, followUpId, forUpdate = true)
                ?: return@inTransaction ChangeFollowUpCadenceResult.NotFound
            if (stored.status != FollowUpStatus.OPEN) {
                return@inTransaction ChangeFollowUpCadenceResult.NotOpen
            }
            val ruleId = stored.ruleId ?: return@inTransaction ChangeFollowUpCadenceResult.NotRecurring
            val rule = getActiveRule(tenantId, ruleId)
                ?: return@inTransaction ChangeFollowUpCadenceResult.NotRecurring
            val dueOn = rule.lastCompletedOn?.plus(cadence) ?: stored.dueOn

            updateRuleCadence(tenantId, ruleId, cadence)
            updateFollowUpSchedule(tenantId, followUpId, cadence, dueOn)
            ChangeFollowUpCadenceResult.Changed(
                checkNotNull(getFollowUp(tenantId, followUpId)).toFollowUp(),
            )
        }
    }

    override suspend fun completeFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        completedOn: LocalDate,
        interaction: Interaction?,
    ): CompleteFollowUpResult = withContext(jdbcDispatcher) {
        followUpRepository.inTransaction {
            val stored = getFollowUp(tenantId, followUpId, forUpdate = true)
                ?: return@inTransaction CompleteFollowUpResult.NotFound
            when (stored.status) {
                FollowUpStatus.DONE -> return@inTransaction CompleteFollowUpResult.AlreadyDone
                FollowUpStatus.CANCELLED -> return@inTransaction CompleteFollowUpResult.Cancelled
                FollowUpStatus.OPEN -> Unit
            }

            val completed = updateCompletion(tenantId, followUpId, completedOn)
            interaction?.let {
                check(interactionRepository.registerInteraction(connection, it.toEntity(tenantId))) {
                    "Generated interaction ID already exists"
                }
            }
            val next = stored.ruleId?.let { ruleId ->
                val rule = checkNotNull(getActiveRule(tenantId, ruleId)) {
                    "Open recurring follow-up has no active rule"
                }
                updateRuleLastCompletion(tenantId, ruleId, completedOn)
                insertSuccessor(
                    tenantId = tenantId,
                    resourceId = stored.resourceId,
                    ruleId = ruleId,
                    dueOn = completedOn.plus(rule.cadence),
                    cadence = rule.cadence,
                ).toFollowUp()
            }
            CompleteFollowUpResult.Completed(
                FollowUpCompletion(completed.toFollowUp(), next),
            )
        }
    }

    override suspend fun cancelFollowUp(tenantId: Uuid, followUpId: Uuid): CancelFollowUpResult =
        withContext(jdbcDispatcher) {
            followUpRepository.inTransaction {
                val stored = getFollowUp(tenantId, followUpId, forUpdate = true)
                    ?: return@inTransaction CancelFollowUpResult.NotFound
                when (stored.status) {
                    FollowUpStatus.DONE -> return@inTransaction CancelFollowUpResult.AlreadyDone
                    FollowUpStatus.CANCELLED -> return@inTransaction CancelFollowUpResult.AlreadyCancelled
                    FollowUpStatus.OPEN -> Unit
                }

                updateStatusCancelled(tenantId, followUpId)
                stored.ruleId?.let { retireRule(tenantId, it) }
                CancelFollowUpResult.Cancelled(
                    checkNotNull(getFollowUp(tenantId, followUpId)).toFollowUp(),
                )
            }
        }

    private fun FollowUpRepository.Transaction.registerOneTimeFollowUp(
        tenantId: Uuid,
        registration: RegisterFollowUp,
    ): RegisterFollowUpResult {
        val stored = insertFollowUp(
            tenantId = tenantId,
            id = registration.id,
            resourceId = registration.resourceId,
            ruleId = null,
            dueOn = registration.dueOn,
            cadence = null,
        ) ?: return RegisterFollowUpResult.IdAlreadyExists
        return RegisterFollowUpResult.Registered(stored.toFollowUp())
    }

    private fun FollowUpRepository.Transaction.registerRecurringFollowUp(
        tenantId: Uuid,
        registration: RegisterFollowUp,
        cadence: FollowUpCadence,
    ): RegisterFollowUpResult {
        val ruleId = newId()
        if (!insertRule(tenantId, ruleId, registration.resourceId, cadence)) {
            return RegisterFollowUpResult.ActiveRecurrenceAlreadyExists
        }
        val stored = insertFollowUp(
            tenantId = tenantId,
            id = registration.id,
            resourceId = registration.resourceId,
            ruleId = ruleId,
            dueOn = registration.dueOn,
            cadence = cadence,
        )
        if (stored == null) {
            deleteRule(ruleId)
            return RegisterFollowUpResult.IdAlreadyExists
        }
        return RegisterFollowUpResult.Registered(stored.toFollowUp())
    }

    private fun FollowUpRepository.Transaction.insertSuccessor(
        tenantId: Uuid,
        resourceId: Uuid,
        ruleId: Uuid,
        dueOn: LocalDate,
        cadence: FollowUpCadence,
    ): FollowUpEntity {
        repeat(ID_GENERATION_ATTEMPTS) {
            insertFollowUp(tenantId, newId(), resourceId, ruleId, dueOn, cadence)?.let { return it }
        }
        error("Could not generate a unique follow-up ID")
    }

    private fun FollowUpEntity.toFollowUp(): FollowUp {
        val schedule = if (ruleId == null) {
            check(cadence == null) { "One-time follow-up has a cadence" }
            FollowUpSchedule.OneTime
        } else {
            FollowUpSchedule.Recurring(checkNotNull(cadence) { "Recurring follow-up has no cadence" })
        }
        return FollowUp(
            id = id,
            resourceId = resourceId,
            dueOn = dueOn,
            dueDateRevision = dueDateRevision,
            schedule = schedule,
            status = status,
            completedOn = completedOn,
            createdAt = createdAt,
        )
    }

    private fun LocalDate.plus(cadence: FollowUpCadence): LocalDate = when (cadence.unit) {
        FollowUpIntervalUnit.DAYS -> plusDays(cadence.amount.toLong())
        FollowUpIntervalUnit.WEEKS -> plusWeeks(cadence.amount.toLong())
        FollowUpIntervalUnit.MONTHS -> plusMonths(cadence.amount.toLong())
        FollowUpIntervalUnit.YEARS -> plusYears(cadence.amount.toLong())
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newId(): Uuid = Uuid.generateV7()

    private fun Interaction.toEntity(tenantId: Uuid): InteractionEntity = InteractionEntity(
        id = id,
        tenantId = tenantId,
        resourceId = resourceId,
        userId = userId,
        channel = channel,
        notes = notes,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    private fun InteractionEntity.toInteraction(): Interaction = Interaction(
        id = id,
        resourceId = resourceId,
        userId = userId,
        channel = channel,
        notes = notes,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    private companion object {
        const val ID_GENERATION_ATTEMPTS = 3
    }
}
