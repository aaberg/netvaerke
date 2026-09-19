package netvaerke.manager.network

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class TenantContactListItemDto(
    val contactId: Uuid,
    val name: String,
    val primaryEmailAddress: String?,
    val image: ContactImageDto?,
)

@Serializable
data class TenantContactDto(
    val contactId: Uuid,
    val name: String,
    val emails: List<EmailAddressDto>,
    val phoneNumbers: List<PhoneNumberDto>,
    val workInfo: WorkInfoDto?,
    val note: NoteDto?,
    val image: ContactImageDto?,
)

@Serializable
data class ContactOverviewDto(
    val contact: TenantContactDto,
    val interactions: List<ContactInteractionDto>,
    val followUps: List<ContactFollowUpDto>,
)

@Serializable
data class ContactInteractionDto(
    val interactionId: Uuid,
    val recordedByUserId: Uuid,
    val channel: InteractionChannelDto,
    val notes: String?,
    val occurredAt: String,
    val createdAt: String,
)

@Serializable
data class CreateContactInteractionDto(
    val channel: InteractionChannelDto,
    val notes: String?,
    val occurredAt: String,
)

@Serializable
data class UpdateContactInteractionDto(
    val channel: InteractionChannelDto,
    val notes: String?,
    val occurredAt: String,
)

@Serializable
enum class InteractionChannelDto {
    PHONE,
    EMAIL,
    TEXT,
    CHAT,
    IN_PERSON,
}

@Serializable
sealed interface CreateContactFollowUpDto {
    @Serializable
    data class OneTime(val dueOn: String) : CreateContactFollowUpDto

    @Serializable
    data class Recurring(
        val frequency: ContactFollowUpFrequencyDto,
        val timeZone: String,
    ) : CreateContactFollowUpDto
}

@Serializable
enum class ContactFollowUpFrequencyDto {
    WEEKLY,
    MONTHLY,
    EVERY_TWO_MONTHS,
    QUARTERLY,
    TWICE_A_YEAR,
    YEARLY,
}

@Serializable
data class ContactFollowUpCadenceDto(
    val amount: Int,
    val unit: ContactFollowUpIntervalUnitDto,
    val frequency: ContactFollowUpFrequencyDto?,
)

@Serializable
enum class ContactFollowUpIntervalUnitDto {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
}

@Serializable
data class ContactFollowUpDto(
    val followUpId: Uuid,
    val dueOn: String,
    val recurrence: ContactFollowUpCadenceDto?,
    val status: ContactFollowUpStatusDto,
    val completedOn: String?,
    val createdAt: String,
)

@Serializable
enum class ContactFollowUpStatusDto {
    OPEN,
    DONE,
    CANCELLED,
}

@Serializable
data class CompleteContactFollowUpDto(
    val timeZone: String,
    val interaction: CreateContactInteractionDto?,
)

@Serializable
data class ContactFollowUpCompletionDto(
    val completed: ContactFollowUpDto,
    val next: ContactFollowUpDto?,
)

@Serializable
data class DueContactFollowUpDto(
    val contact: TenantContactListItemDto,
    val followUp: ContactFollowUpDto,
)

@Serializable
data class CreateNewContactDto(
    val name: String,
    val emails: List<EmailAddressDto>,
    val phoneNumbers: List<PhoneNumberDto>,
    val workInfo: WorkInfoDto?,
    val note: NoteDto?,
)

@Serializable
data class UpdateContactDto(
    val name: String,
    val emails: List<EmailAddressDto>,
    val phoneNumbers: List<PhoneNumberDto>,
    val workInfo: WorkInfoDto?,
    val note: NoteDto?,
)

@Serializable
data class ContactImageUploadDto(
    val fileKey: String,
)

@Serializable
data class ContactImageUpdateDto(
    val previousFileKey: String?,
)
