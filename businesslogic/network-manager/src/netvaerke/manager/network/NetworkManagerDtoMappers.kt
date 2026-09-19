package netvaerke.manager.network

import java.time.Instant
import java.time.LocalDate
import netvaerke.access.engagement.Interaction
import netvaerke.access.engagement.InteractionChannel
import netvaerke.access.engagement.FollowUp
import netvaerke.access.engagement.FollowUpCadence
import netvaerke.access.engagement.FollowUpCompletion
import netvaerke.access.engagement.FollowUpIntervalUnit
import netvaerke.access.engagement.FollowUpSchedule
import netvaerke.access.contact.Contact
import netvaerke.access.contact.ContactImage
import netvaerke.access.contact.EmailAddress
import netvaerke.access.contact.Note
import netvaerke.access.contact.PhoneNumber
import netvaerke.access.contact.WorkInfo

internal fun Contact.toListItemDto(): TenantContactListItemDto {

    val emails = contactDetails.filterIsInstance<EmailAddress>()
    val primaryEmail = emails.firstOrNull { it.isPrimary } ?: emails.firstOrNull()
    val contactImage = contactDetails.filterIsInstance<ContactImage>().firstOrNull()

    return TenantContactListItemDto(
        contactId = id,
        name = name,
        primaryEmailAddress = primaryEmail?.value,
        image = contactImage?.toDto(),
    )
}

internal fun Contact.toDto(): TenantContactDto = TenantContactDto(
    contactId = id,
    name = name,
    emails = contactDetails.filterIsInstance<EmailAddress>().map(EmailAddress::toDto),
    phoneNumbers = contactDetails.filterIsInstance<PhoneNumber>().map(PhoneNumber::toDto),
    workInfo = contactDetails.filterIsInstance<WorkInfo>().firstOrNull()?.toDto(),
    note = contactDetails.filterIsInstance<Note>().firstOrNull()?.toDto(),
    image = contactDetails.filterIsInstance<ContactImage>().firstOrNull()?.toDto(),
)

internal fun CreateNewContactDto.toContact(contactId: kotlin.uuid.Uuid): Contact = Contact(
    id = contactId,
    name = name,
    contactDetails = buildList {
        addAll(emails.map(EmailAddressDto::toContactDetail))
        addAll(phoneNumbers.map(PhoneNumberDto::toContactDetail))
        workInfo?.toContactDetail()?.let(::add)
        note?.toContactDetail()?.let(::add)
    },
)

internal fun UpdateContactDto.toContact(contactId: kotlin.uuid.Uuid, image: ContactImageDto?): Contact = Contact(
    id = contactId,
    name = name,
    contactDetails = buildList {
        addAll(emails.map(EmailAddressDto::toContactDetail))
        addAll(phoneNumbers.map(PhoneNumberDto::toContactDetail))
        workInfo?.toContactDetail()?.let(::add)
        note?.toContactDetail()?.let(::add)
        image?.toContactDetail()?.let(::add)
    },
)

internal fun EmailAddress.toDto(): EmailAddressDto = EmailAddressDto(value, isPrimary, label)

internal fun PhoneNumber.toDto(): PhoneNumberDto = PhoneNumberDto(value, label)

internal fun WorkInfo.toDto(): WorkInfoDto = WorkInfoDto(title, organization)

internal fun Note.toDto(): NoteDto = NoteDto(value)

internal fun ContactImage.toDto(): ContactImageDto = ContactImageDto(fileKey)

internal fun Interaction.toDto(): ContactInteractionDto = ContactInteractionDto(
    interactionId = id,
    recordedByUserId = userId,
    channel = channel.toDto(),
    notes = notes,
    occurredAt = occurredAt.toString(),
    createdAt = checkNotNull(createdAt).toString(),
)

internal fun CreateContactInteractionDto.toInteraction(
    contactId: kotlin.uuid.Uuid,
    actorId: kotlin.uuid.Uuid,
    interactionId: kotlin.uuid.Uuid,
): Interaction = Interaction(
    id = interactionId,
    resourceId = contactId,
    userId = actorId,
    channel = channel.toInteractionChannel(),
    notes = notes,
    occurredAt = occurredAt.toInstant("Interaction occurrence time"),
)

internal fun Interaction.update(update: UpdateContactInteractionDto): Interaction = copy(
    channel = update.channel.toInteractionChannel(),
    notes = update.notes,
    occurredAt = update.occurredAt.toInstant("Interaction occurrence time"),
)

internal fun ContactFollowUpFrequencyDto.toCadence(): FollowUpCadence = when (this) {
    ContactFollowUpFrequencyDto.WEEKLY -> FollowUpCadence(1, FollowUpIntervalUnit.WEEKS)
    ContactFollowUpFrequencyDto.MONTHLY -> FollowUpCadence(1, FollowUpIntervalUnit.MONTHS)
    ContactFollowUpFrequencyDto.EVERY_TWO_MONTHS -> FollowUpCadence(2, FollowUpIntervalUnit.MONTHS)
    ContactFollowUpFrequencyDto.QUARTERLY -> FollowUpCadence(3, FollowUpIntervalUnit.MONTHS)
    ContactFollowUpFrequencyDto.TWICE_A_YEAR -> FollowUpCadence(6, FollowUpIntervalUnit.MONTHS)
    ContactFollowUpFrequencyDto.YEARLY -> FollowUpCadence(1, FollowUpIntervalUnit.YEARS)
}

internal fun FollowUp.toDto(): ContactFollowUpDto = ContactFollowUpDto(
    followUpId = id,
    dueOn = dueOn.toString(),
    recurrence = when (val storedSchedule = schedule) {
        FollowUpSchedule.OneTime -> null
        is FollowUpSchedule.Recurring -> storedSchedule.cadence.toDto()
    },
    status = ContactFollowUpStatusDto.valueOf(status.name),
    completedOn = completedOn?.toString(),
    createdAt = createdAt.toString(),
)

internal fun FollowUpCompletion.toDto(): ContactFollowUpCompletionDto = ContactFollowUpCompletionDto(
    completed = completed.toDto(),
    next = next?.toDto(),
)

private fun FollowUpCadence.toDto(): ContactFollowUpCadenceDto = ContactFollowUpCadenceDto(
    amount = amount,
    unit = ContactFollowUpIntervalUnitDto.valueOf(unit.name),
    frequency = when (unit) {
        FollowUpIntervalUnit.DAYS -> if (amount == 7) ContactFollowUpFrequencyDto.WEEKLY else null
        FollowUpIntervalUnit.WEEKS -> if (amount == 1) ContactFollowUpFrequencyDto.WEEKLY else null
        FollowUpIntervalUnit.MONTHS -> when (amount) {
            1 -> ContactFollowUpFrequencyDto.MONTHLY
            2 -> ContactFollowUpFrequencyDto.EVERY_TWO_MONTHS
            3 -> ContactFollowUpFrequencyDto.QUARTERLY
            6 -> ContactFollowUpFrequencyDto.TWICE_A_YEAR
            12 -> ContactFollowUpFrequencyDto.YEARLY
            else -> null
        }
        FollowUpIntervalUnit.YEARS -> if (amount == 1) ContactFollowUpFrequencyDto.YEARLY else null
    },
)

private fun InteractionChannel.toDto(): InteractionChannelDto = InteractionChannelDto.valueOf(name)

private fun InteractionChannelDto.toInteractionChannel(): InteractionChannel = InteractionChannel.valueOf(name)

private fun String.toInstant(field: String): Instant = try {
    Instant.parse(this)
} catch (exception: Exception) {
    throw IllegalArgumentException("$field must be an ISO-8601 instant", exception)
}

internal fun String.toLocalDate(field: String): LocalDate = try {
    LocalDate.parse(this)
} catch (exception: Exception) {
    throw IllegalArgumentException("$field must be an ISO-8601 local date", exception)
}

private fun EmailAddressDto.toContactDetail(): EmailAddress = EmailAddress(value, isPrimary, label)

private fun PhoneNumberDto.toContactDetail(): PhoneNumber = PhoneNumber(value, label)

private fun WorkInfoDto.toContactDetail(): WorkInfo = WorkInfo(title, organization)

private fun NoteDto.toContactDetail(): Note = Note(value)

private fun ContactImageDto.toContactDetail(): ContactImage = ContactImage(fileKey)
