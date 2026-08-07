package nettvaerke.access.contact.repository

import java.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ContactEntity(
    val id: Uuid,
    val name: String,
    val tenantId: Uuid,
    val details: List<ContactDetailEntity>,
    val createdAt: Instant? = null,
)

@Serializable
sealed class ContactDetailEntity

@Serializable
@SerialName("emailAddress")
data class EmailAddressEntity(
    val value: String,
    val isPrimary: Boolean,
    val label: String? = null,
) : ContactDetailEntity()

@Serializable
@SerialName("phoneNumber")
data class PhoneNumberEntity(
    val value: String,
    val label: String? = null,
) : ContactDetailEntity()

@Serializable
@SerialName("note")
data class NoteEntity(
    val value: String,
) : ContactDetailEntity()

@Serializable
@SerialName("workInfo")
data class WorkInfoEntity(
    val title: String? = null,
    val organization: String? = null,
) : ContactDetailEntity()

@Serializable
@SerialName("contactImage")
data class ContactImageEntity(
    val fileKey: String,
    val mimeType: String,
) : ContactDetailEntity()
