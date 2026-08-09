package netvaerke.access.contact

sealed class ContactDetail

data class EmailAddress(
    val value: String,
    val isPrimary: Boolean,
    val label: String? = null
) : ContactDetail()

data class PhoneNumber(
    val value: String,
    val label: String? = null
) : ContactDetail()

data class Note(
    val value: String,
) : ContactDetail()

data class WorkInfo(
    val title: String? = null,
    val organization: String? = null,
) : ContactDetail()

data class ContactImage(
    val fileKey: String,
    val mimeType: String,
) : ContactDetail()
