package netvaerke.businesslogic.network

import netvaerke.access.contact.Contact
import netvaerke.access.contact.EmailAddress

private fun Contact.toDto(): TenantContactListItemDto {

    val emails = contactDetails.filterIsInstance<EmailAddress>()
    val primaryEmail = emails.firstOrNull { it.isPrimary } ?: emails.firstOrNull()

    return TenantContactListItemDto(
        contactId = id,
        name = name,
        primaryEmailAddress = primaryEmail?.value,
        image = null
    )
}