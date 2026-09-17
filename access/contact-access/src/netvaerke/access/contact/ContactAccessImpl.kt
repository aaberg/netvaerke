package netvaerke.access.contact

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val jdbcDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ContactAccess {
    override suspend fun saveContact(tenantId: Uuid, contact: Contact): Boolean =
        withContext(jdbcDispatcher) {
            repository.saveContact(contact.toEntity(tenantId))
        }

    override suspend fun deleteContact(tenantId: Uuid, contactId: Uuid): Boolean =
        withContext(jdbcDispatcher) {
            repository.markContactDeleted(tenantId, contactId)
        }

    override suspend fun getContact(tenantId: Uuid, id: Uuid): Contact? =
        withContext(jdbcDispatcher) {
            repository.getContact(tenantId, id)
        }?.toContact()

    override suspend fun getContacts(tenantId: Uuid): List<Contact> =
        withContext(jdbcDispatcher) {
            repository.getContacts(tenantId)
        }.map { it.toContact() }

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
        is ContactImage -> ContactImageEntity(fileKey)
    }

    private fun ContactDetailEntity.toContactDetail(): ContactDetail = when (this) {
        is EmailAddressEntity -> EmailAddress(value, isPrimary, label)
        is PhoneNumberEntity -> PhoneNumber(value, label)
        is NoteEntity -> Note(value)
        is WorkInfoEntity -> WorkInfo(title, organization)
        is ContactImageEntity -> ContactImage(fileKey)
    }
}
