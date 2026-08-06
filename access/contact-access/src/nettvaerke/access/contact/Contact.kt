package nettvaerke.access.contact

import kotlin.uuid.Uuid

data class Contact(
    val id: Uuid,
    val name: String,
)
