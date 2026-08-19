package netvaerke.access.contact

import kotlin.uuid.Uuid

data class Contact(
    val id: Uuid,
    val name: String,
    val contactDetails: List<ContactDetail>
)
