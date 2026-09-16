package netvaerke.access.engagement

import java.time.Instant
import java.time.LocalDate
import kotlin.uuid.Uuid

sealed interface FollowUpSchedule {
    data object OneTime : FollowUpSchedule

    data class Recurring(
        val cadence: FollowUpCadence,
    ) : FollowUpSchedule
}

data class FollowUpCadence(
    val amount: Int,
    val unit: FollowUpIntervalUnit,
) {
    init {
        require(amount > 0) { "Follow-up cadence amount must be positive" }
    }
}

enum class FollowUpIntervalUnit {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
}

data class FollowUp(
    val id: Uuid,
    val resourceId: Uuid,
    val dueOn: LocalDate,
    val dueDateRevision: Long,
    val schedule: FollowUpSchedule,
    val status: FollowUpStatus,
    val completedOn: LocalDate?,
    val createdAt: Instant,
)

enum class FollowUpStatus {
    OPEN,
    DONE,
    CANCELLED,
}

data class RegisterFollowUp(
    val id: Uuid,
    val resourceId: Uuid,
    val dueOn: LocalDate,
    val schedule: FollowUpSchedule,
)

sealed interface RegisterFollowUpResult {
    data class Registered(val followUp: FollowUp) : RegisterFollowUpResult

    data object IdAlreadyExists : RegisterFollowUpResult

    data object ActiveRecurrenceAlreadyExists : RegisterFollowUpResult
}

sealed interface RescheduleFollowUpResult {
    data class Rescheduled(val followUp: FollowUp) : RescheduleFollowUpResult

    data object NotFound : RescheduleFollowUpResult

    data object NotOpen : RescheduleFollowUpResult
}

sealed interface ChangeFollowUpCadenceResult {
    data class Changed(val followUp: FollowUp) : ChangeFollowUpCadenceResult

    data object NotFound : ChangeFollowUpCadenceResult

    data object NotOpen : ChangeFollowUpCadenceResult

    data object NotRecurring : ChangeFollowUpCadenceResult
}

data class FollowUpCompletion(
    val completed: FollowUp,
    val next: FollowUp?,
)

sealed interface CompleteFollowUpResult {
    data class Completed(val completion: FollowUpCompletion) : CompleteFollowUpResult

    data object NotFound : CompleteFollowUpResult

    data object AlreadyDone : CompleteFollowUpResult

    data object Cancelled : CompleteFollowUpResult
}

sealed interface CancelFollowUpResult {
    data class Cancelled(val followUp: FollowUp) : CancelFollowUpResult

    data object NotFound : CancelFollowUpResult

    data object AlreadyCancelled : CancelFollowUpResult

    data object AlreadyDone : CancelFollowUpResult
}
