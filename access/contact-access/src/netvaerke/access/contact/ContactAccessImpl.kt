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
    override fun saveContact(tenantId: Uuid, contact: Contact) {
        val existingContactEntity = repository.getContact(contact.id)

        if (existingContactEntity != null && existingContactEntity.tenantId != tenantId) {
            throw IllegalArgumentException("Error updating contact. Contact is not part of tenant with id: $tenantId")
        }

        repository.saveContact(contact.toEntity(tenantId))
    }

    override fun deleteContact(tenantId: Uuid, contact: Contact) {
        val existingContact = getContact(tenantId, contact.id)
            ?: throw IllegalArgumentException("Error deleting contact. Contact not found on tenant: $tenantId")

        repository.deleteContact(contact.toEntity(tenantId))
    }

    override fun getContact(tenantId: Uuid, id: Uuid): Contact? {
        val contactEntity = repository.getContact(id) ?: return null

        if (contactEntity.tenantId != tenantId) return null

        return contactEntity.toContact()
    }


    override fun getContacts(tenantId: Uuid): List<Contact> =
        repository.getContacts(tenantId).map { it.toContact() }

    private fun Contact.toEntity(tenantId: Uuid): ContactEntity = ContactEntity(
        id = id,
        name = name,
        tenantId = tenantId,
        details = contactDetails.map { it.toEntity() },
    )

    private fun ContactEntity.toContact(): Contact =
        Contact(
            id = id,
            name = name,
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
