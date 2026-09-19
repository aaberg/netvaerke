package netvaerke.application.web

import io.ktor.http.Parameters
import java.time.LocalDate
import netvaerke.manager.network.ContactFollowUpDto
import netvaerke.manager.network.ContactFollowUpFrequencyDto
import netvaerke.manager.network.CreateContactFollowUpDto

internal data class ContactFollowUpForm(
    val dueOn: String,
    val recurrence: String = RECURRENCE_NONE,
    val frequency: String = ContactFollowUpFrequencyDto.WEEKLY.name,
) {
    fun validationError(): String? = when {
        recurrence !in RECURRENCE_OPTIONS -> "Choose whether this follow-up repeats."
        recurrence == RECURRENCE_NONE && dueOn.toLocalDateOrNull() == null -> "Enter a valid due date."
        recurrence == RECURRENCE_RECURRING && frequency.toFollowUpFrequency() == null -> "Choose a frequency."
        else -> null
    }

    fun toCreateDto(timeZone: String): CreateContactFollowUpDto = when (recurrence) {
        RECURRENCE_NONE -> CreateContactFollowUpDto.OneTime(
            dueOn = checkNotNull(dueOn.toLocalDateOrNull()).toString(),
        )
        RECURRENCE_RECURRING -> CreateContactFollowUpDto.Recurring(
            frequency = checkNotNull(frequency.toFollowUpFrequency()),
            timeZone = timeZone,
        )
        else -> error("Unknown follow-up recurrence: $recurrence")
    }

    companion object {
        const val RECURRENCE_NONE = "NONE"
        const val RECURRENCE_RECURRING = "RECURRING"
        private val RECURRENCE_OPTIONS = setOf(RECURRENCE_NONE, RECURRENCE_RECURRING)

        fun forToday(today: LocalDate): ContactFollowUpForm = ContactFollowUpForm(today.toString())
    }
}

internal data class ContactFollowUpFrequencyForm(
    val frequency: String,
) {
    fun validationError(): String? = if (frequency.toFollowUpFrequency() == null) "Choose a frequency." else null

    fun toDto(): ContactFollowUpFrequencyDto = checkNotNull(frequency.toFollowUpFrequency())

    companion object {
        fun from(followUp: ContactFollowUpDto): ContactFollowUpFrequencyForm = ContactFollowUpFrequencyForm(
            frequency = followUp.recurrence?.frequency?.name.orEmpty(),
        )
    }
}

internal data class ContactFollowUpFrequencyOption(
    val value: String,
    val label: String,
)

internal fun contactFollowUpFrequencyOptions(): List<ContactFollowUpFrequencyOption> =
    ContactFollowUpFrequencyDto.entries.map { frequency ->
        ContactFollowUpFrequencyOption(frequency.name, frequency.displayName())
    }

internal fun Parameters.toContactFollowUpForm(): ContactFollowUpForm = ContactFollowUpForm(
    dueOn = this["dueOn"]?.trim().orEmpty(),
    recurrence = this["recurrence"]?.trim().orEmpty(),
    frequency = this["frequency"]?.trim().orEmpty(),
)

internal fun Parameters.toContactFollowUpFrequencyForm(): ContactFollowUpFrequencyForm = ContactFollowUpFrequencyForm(
    frequency = this["frequency"]?.trim().orEmpty(),
)

internal fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun String.toFollowUpFrequency(): ContactFollowUpFrequencyDto? =
    ContactFollowUpFrequencyDto.entries.firstOrNull { it.name == this }

internal fun ContactFollowUpFrequencyDto.displayName(): String = when (this) {
    ContactFollowUpFrequencyDto.WEEKLY -> "Weekly"
    ContactFollowUpFrequencyDto.MONTHLY -> "Monthly"
    ContactFollowUpFrequencyDto.EVERY_TWO_MONTHS -> "Every two months"
    ContactFollowUpFrequencyDto.QUARTERLY -> "Quarterly"
    ContactFollowUpFrequencyDto.TWICE_A_YEAR -> "Twice a year"
    ContactFollowUpFrequencyDto.YEARLY -> "Yearly"
}
