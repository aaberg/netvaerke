package netvaerke.businesslogic.network

import kotlinx.serialization.Serializable
import netvaerke.access.contact.ContactImage
import netvaerke.access.contact.EmailAddress
import netvaerke.access.contact.Note
import netvaerke.access.contact.PhoneNumber
import netvaerke.access.contact.WorkInfo
import kotlin.uuid.Uuid

@Serializable
data class TenantContactListItemDto(
    val contactId: Uuid,
    val name: String,
    val mainEmailAddress: String?,
    val image: ContactImage?
)

@Serializable
data class TenantContactDto(
    val contactId: Uuid,
    val name: String,
    val emails: List<EmailAddress>,
    val phoneNumbers: List<PhoneNumber>,
    val workInfo: WorkInfo?,
    val note: Note?,
    val image: ContactImage?
)

@Serializable
data class CreateNewContactDto(
    val name: String,
    val emails: List<EmailAddress>,
    val phoneNumbers: List<PhoneNumber>,
    val workInfo: WorkInfo?,
    val note: Note?,
    val image: ContactImage?
)

@Serializable
data class UpdateContactDto(
    val name: String,
    val emails: List<EmailAddress>,
    val phoneNumbers: List<PhoneNumber>,
    val workInfo: WorkInfo?,
    val note: Note?,
    val image: ContactImage?
)