package netvaerke.access.contact

import netvaerke.access.contact.repository.ContactDetailEntity
import netvaerke.access.contact.repository.ContactEntity
import netvaerke.access.contact.repository.ContactImageEntity
import netvaerke.access.contact.repository.ContactRepository
import netvaerke.access.contact.repository.EmailAddressEntity
import netvaerke.access.contact.repository.NoteEntity
import netvaerke.access.contact.repository.PhoneNumberEntity
import netvaerke.access.contact.repository.WorkInfoEntity
import kotlin.uuid.Uuid

class ContactAccessImpl(
    private val repository: ContactRepository,
) : ContactAccess {
    override fun saveContact(contact: Contact) {
        repository.saveContact(contact.toEntity())
    }

    override fun deleteContact(contact: Contact) {
        repository.deleteContact(contact.toEntity())
    }

    override fun getContact(id: Uuid): Contact? =
        repository.getContact(id)?.toContact()

    override fun getContacts(tenantId: Uuid): List<Contact> =
        repository.getContacts(tenantId).map { it.toContact() }

    private fun Contact.toEntity(): ContactEntity = ContactEntity(
        id = id,
        name = name,
        tenantId = tenantId,
        details = contactDetails.map { it.toEntity() },
    )

    private fun ContactEntity.toContact(): Contact =
        Contact(
            id = id,
            name = name,
            tenantId = tenantId,
            contactDetails = details.map { it.toContactDetail() },
        )

    private fun ContactDetail.toEntity(): ContactDetailEntity = when (this) {
        is EmailAddress -> EmailAddressEntity(value, isPrimary, label)
        is PhoneNumber -> PhoneNumberEntity(value, label)
        is Note -> NoteEntity(value)
        is WorkInfo -> WorkInfoEntity(title, organization)
        is ContactImage -> ContactImageEntity(fileKey, mimeType)
    }

    private fun ContactDetailEntity.toContactDetail(): ContactDetail = when (this) {
        is EmailAddressEntity -> EmailAddress(value, isPrimary, label)
        is PhoneNumberEntity -> PhoneNumber(value, label)
        is NoteEntity -> Note(value)
        is WorkInfoEntity -> WorkInfo(title, organization)
        is ContactImageEntity -> ContactImage(fileKey, mimeType)
    }
}
