package netvaerke.businesslogic.network

import netvaerke.access.contact.Contact
import netvaerke.access.contact.ContactImage
import netvaerke.access.contact.EmailAddress
import netvaerke.access.contact.Note
import netvaerke.access.contact.PhoneNumber
import netvaerke.access.contact.WorkInfo

internal fun Contact.toListItemDto(): TenantContactListItemDto {

    val emails = contactDetails.filterIsInstance<EmailAddress>()
    val primaryEmail = emails.firstOrNull { it.isPrimary } ?: emails.firstOrNull()
    val contactImage = contactDetails.filterIsInstance<ContactImage>().firstOrNull()

    return TenantContactListItemDto(
        contactId = id,
        name = name,
        primaryEmailAddress = primaryEmail?.value,
        image = contactImage
    )
}

internal fun Contact.toDto(): TenantContactDto = TenantContactDto(
    contactId = id,
    name = name,
    emails = contactDetails.filterIsInstance<EmailAddress>(),
    phoneNumbers = contactDetails.filterIsInstance<PhoneNumber>(),
    workInfo = contactDetails.filterIsInstance<WorkInfo>().firstOrNull(),
    note = contactDetails.filterIsInstance<Note>().firstOrNull(),
    image = contactDetails.filterIsInstance<ContactImage>().firstOrNull(),
)

internal fun CreateNewContactDto.toContact(contactId: kotlin.uuid.Uuid): Contact = Contact(
    id = contactId,
    name = name,
    contactDetails = buildList {
        addAll(emails)
        addAll(phoneNumbers)
        workInfo?.let(::add)
        note?.let(::add)
    },
)

internal fun UpdateContactDto.toContact(contactId: kotlin.uuid.Uuid, image: ContactImage?): Contact = Contact(
    id = contactId,
    name = name,
    contactDetails = buildList {
        addAll(emails)
        addAll(phoneNumbers)
        workInfo?.let(::add)
        note?.let(::add)
        image?.let(::add)
    },
)
