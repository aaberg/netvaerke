package netvaerke.businesslogic.network

import kotlinx.serialization.Serializable

@Serializable
data class EmailAddressDto(
    val value: String,
    val isPrimary: Boolean,
    val label: String? = null,
)

@Serializable
data class PhoneNumberDto(
    val value: String,
    val label: String? = null,
)

@Serializable
data class NoteDto(
    val value: String,
)

@Serializable
data class WorkInfoDto(
    val title: String? = null,
    val organization: String? = null,
)

@Serializable
data class ContactImageDto(
    val fileKey: String,
)
