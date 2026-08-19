package netvaerke.access.contact

import kotlinx.serialization.Serializable

@Serializable
sealed class ContactDetail

@Serializable
data class EmailAddress(
    val value: String,
    val isPrimary: Boolean,
    val label: String? = null
) : ContactDetail()

@Serializable
data class PhoneNumber(
    val value: String,
    val label: String? = null
) : ContactDetail()

@Serializable
data class Note(
    val value: String,
) : ContactDetail()

@Serializable
data class WorkInfo(
    val title: String? = null,
    val organization: String? = null,
) : ContactDetail()

@Serializable
data class ContactImage(
    val fileKey: String,
    val mimeType: String,
) : ContactDetail()
