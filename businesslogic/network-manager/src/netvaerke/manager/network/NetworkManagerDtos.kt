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
