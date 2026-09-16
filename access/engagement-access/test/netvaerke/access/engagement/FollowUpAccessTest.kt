package netvaerke.access.engagement

import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import netvaerke.access.engagement.repository.EngagementTestDatabase
import netvaerke.access.engagement.repository.FollowUpRepository
import netvaerke.access.engagement.repository.InteractionRepository

class FollowUpAccessTest {
    private val dataSource: DataSource
        get() = EngagementTestDatabase.dataSource

    private val access: EngagementAccess
        get() = EngagementAccessImpl(InteractionRepository(dataSource), FollowUpRepository(dataSource))

    @BeforeTest
    fun clearFollowUps() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("TRUNCATE TABLE engagement.follow_up, engagement.follow_up_rule")
            }
        }
    }

    @Test
    fun `registers reschedules and completes a one-time follow-up`() = runBlocking {
        val tenantId = randomFollowUpUuid()
        val resourceId = randomFollowUpUuid()
        val id = randomFollowUpUuid()
        val registration = RegisterFollowUp(
            id = id,
            resourceId = resourceId,
            dueOn = LocalDate.parse("2026-10-01"),
            schedule = FollowUpSchedule.OneTime,
        )

        val registered = assertIs<RegisterFollowUpResult.Registered>(
            access.registerFollowUp(tenantId, registration),
        ).followUp
        assertEquals(1, registered.dueDateRevision)
        assertEquals(emptyList(), access.getOpenFollowUpsDueBy(tenantId, LocalDate.parse("2026-09-30")))
        assertEquals(listOf(registered), access.getOpenFollowUpsDueBy(tenantId, LocalDate.parse("2026-10-01")))
        assertNull(access.getFollowUp(randomFollowUpUuid(), id))
        assertEquals(
            CompleteFollowUpResult.NotFound,
            access.completeFollowUp(randomFollowUpUuid(), id, LocalDate.parse("2026-10-01")),
        )

        val unchanged = assertIs<RescheduleFollowUpResult.Rescheduled>(
            access.rescheduleFollowUp(tenantId, id, LocalDate.parse("2026-10-01")),
        ).followUp
        assertEquals(1, unchanged.dueDateRevision)
        val rescheduled = assertIs<RescheduleFollowUpResult.Rescheduled>(
            access.rescheduleFollowUp(tenantId, id, LocalDate.parse("2026-10-15")),
        ).followUp
        assertEquals(2, rescheduled.dueDateRevision)
        assertEquals(LocalDate.parse("2026-10-15"), rescheduled.dueOn)

        val completion = assertIs<CompleteFollowUpResult.Completed>(
            access.completeFollowUp(tenantId, id, LocalDate.parse("2026-10-16")),
        ).completion
        assertEquals(FollowUpStatus.DONE, completion.completed.status)
        assertEquals(LocalDate.parse("2026-10-16"), completion.completed.completedOn)
        assertNull(completion.next)
        assertEquals(emptyList(), access.getOpenFollowUpsDueBy(tenantId, LocalDate.parse("2026-12-01")))
        assertEquals(CompleteFollowUpResult.AlreadyDone, access.completeFollowUp(tenantId, id, LocalDate.parse("2026-10-17")))
    }

    @Test
    fun `completing recurring work schedules from its actual completion date`() = runBlocking {
        val tenantId = randomFollowUpUuid()
        val resourceId = randomFollowUpUuid()
        val monthly = FollowUpCadence(1, FollowUpIntervalUnit.MONTHS)
        val first = RegisterFollowUp(
            id = randomFollowUpUuid(),
            resourceId = resourceId,
            dueOn = LocalDate.parse("2026-01-31"),
            schedule = FollowUpSchedule.Recurring(monthly),
        )
        assertIs<RegisterFollowUpResult.Registered>(access.registerFollowUp(tenantId, first))
        assertEquals(
            RegisterFollowUpResult.ActiveRecurrenceAlreadyExists,
            access.registerFollowUp(tenantId, first.copy(id = randomFollowUpUuid())),
        )

        val firstCompletion = assertIs<CompleteFollowUpResult.Completed>(
            access.completeFollowUp(tenantId, first.id, LocalDate.parse("2026-02-02")),
        ).completion
        val second = requireNotNull(firstCompletion.next)
        assertEquals(LocalDate.parse("2026-03-02"), second.dueOn)
        assertEquals(1, second.dueDateRevision)
        assertEquals(
            CompleteFollowUpResult.AlreadyDone,
            access.completeFollowUp(tenantId, first.id, LocalDate.parse("2026-02-03")),
        )
        assertEquals(
            listOf(second),
            access.getResourceFollowUps(tenantId, resourceId).filter { it.status == FollowUpStatus.OPEN },
        )

        val everyTwoMonths = FollowUpCadence(2, FollowUpIntervalUnit.MONTHS)
        val changed = assertIs<ChangeFollowUpCadenceResult.Changed>(
            access.changeFollowUpCadence(tenantId, second.id, everyTwoMonths),
        ).followUp
        assertEquals(LocalDate.parse("2026-04-02"), changed.dueOn)
        assertEquals(2, changed.dueDateRevision)
        assertEquals(FollowUpSchedule.Recurring(everyTwoMonths), changed.schedule)

        val secondCompletion = assertIs<CompleteFollowUpResult.Completed>(
            access.completeFollowUp(tenantId, second.id, LocalDate.parse("2026-04-05")),
        ).completion
        val third = requireNotNull(secondCompletion.next)
        assertEquals(LocalDate.parse("2026-06-05"), third.dueOn)
        assertEquals(FollowUpSchedule.Recurring(everyTwoMonths), third.schedule)

        val history = access.getResourceFollowUps(tenantId, resourceId)
        assertEquals(3, history.size)
        assertEquals(FollowUpSchedule.Recurring(monthly), history.single { it.id == first.id }.schedule)
        assertEquals(FollowUpSchedule.Recurring(everyTwoMonths), history.single { it.id == second.id }.schedule)
    }

    @Test
    fun `cancelling recurring work stops its series and allows a replacement`() = runBlocking {
        val tenantId = randomFollowUpUuid()
        val resourceId = randomFollowUpUuid()
        val recurring = RegisterFollowUp(
            id = randomFollowUpUuid(),
            resourceId = resourceId,
            dueOn = LocalDate.parse("2026-10-01"),
            schedule = FollowUpSchedule.Recurring(FollowUpCadence(2, FollowUpIntervalUnit.WEEKS)),
        )
        assertIs<RegisterFollowUpResult.Registered>(access.registerFollowUp(tenantId, recurring))

        val cancelled = assertIs<CancelFollowUpResult.Cancelled>(
            access.cancelFollowUp(tenantId, recurring.id),
        ).followUp
        assertEquals(FollowUpStatus.CANCELLED, cancelled.status)
        assertEquals(
            CompleteFollowUpResult.Cancelled,
            access.completeFollowUp(tenantId, recurring.id, LocalDate.parse("2026-10-01")),
        )

        val replacement = recurring.copy(id = randomFollowUpUuid())
        assertIs<RegisterFollowUpResult.Registered>(access.registerFollowUp(tenantId, replacement))

        val oneTime = RegisterFollowUp(
            id = randomFollowUpUuid(),
            resourceId = resourceId,
            dueOn = LocalDate.parse("2026-11-01"),
            schedule = FollowUpSchedule.OneTime,
        )
        assertIs<RegisterFollowUpResult.Registered>(access.registerFollowUp(tenantId, oneTime))
        assertEquals(
            ChangeFollowUpCadenceResult.NotRecurring,
            access.changeFollowUpCadence(
                tenantId,
                oneTime.id,
                FollowUpCadence(1, FollowUpIntervalUnit.YEARS),
            ),
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomFollowUpUuid(): Uuid = Uuid.generateV7()
