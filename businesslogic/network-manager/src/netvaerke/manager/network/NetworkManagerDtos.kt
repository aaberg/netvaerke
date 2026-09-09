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
