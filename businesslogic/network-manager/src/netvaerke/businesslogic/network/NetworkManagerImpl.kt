package netvaerke.businesslogic.network

import java.util.UUID
import netvaerke.access.contact.ContactAccess
import netvaerke.access.contact.ContactImage
import netvaerke.engine.authorization.AuthorizationEngine
import netvaerke.engine.authorization.Operation
import kotlin.uuid.Uuid

class NetworkManagerImpl(
    private val authorizer: AuthorizationEngine,
    private val contactAccess: ContactAccess
) : NetworkManager {

    override suspend fun getTenantContacts(
        tenantId: Uuid,
        actorId: Uuid,
    ): List<TenantContactListItemDto> {
        authorize(actorId, tenantId, Operation.READ_CONTACTS)
        return contactAccess.getContacts(tenantId).map { it.toListItemDto() }
    }

    override suspend fun getContact(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
    ): TenantContactDto? {
        if (!authorizer.authorize(actorId, tenantId, Operation.READ_CONTACTS).authorized) return null
        return contactAccess.getContact(tenantId, contactId)?.toDto()
    }

    override suspend fun createNewContact(
        tenantId: Uuid,
        actorId: Uuid,
        createNewContactDto: CreateNewContactDto,
    ): TenantContactDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        createNewContactDto.validateDetailPolicy()

        val contact = createNewContactDto.toContact(randomUuid())
        check(contactAccess.saveContact(tenantId, contact)) { "Generated contact ID already exists" }
        return contact.toDto()
    }

    override suspend fun updateContact(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        updateContactDto: UpdateContactDto,
    ) {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        updateContactDto.validateDetailPolicy()

        val existingContact = findContact(tenantId, contactId)
        val image = existingContact.contactDetails.filterIsInstance<ContactImage>().firstOrNull()
        saveContact(tenantId, updateContactDto.toContact(contactId, image?.toDto()))
    }

    override suspend fun deleteContact(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
    ) {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        if (!contactAccess.deleteContact(tenantId, contactId)) throw ContactNotFoundException()
    }

    override suspend fun reserveContactImageUpload(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
    ): ContactImageUploadDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        return ContactImageUploadDto("tenants/$tenantId/contacts/$contactId/images/${UUID.randomUUID()}")
    }

    override suspend fun setContactImage(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        fileKey: String?,
    ): ContactImageUpdateDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        require(fileKey == null || fileKey.isNotBlank()) { "Image file key must not be blank" }

        val existingContact = findContact(tenantId, contactId)
        val previousImage = existingContact.contactDetails.filterIsInstance<ContactImage>().firstOrNull()
        val updatedContact = existingContact.copy(
            contactDetails = existingContact.contactDetails.filterNot { it is ContactImage } +
                listOfNotNull(fileKey?.let(::ContactImage)),
        )
        saveContact(tenantId, updatedContact)
        return ContactImageUpdateDto(previousImage?.fileKey)
    }

    private suspend fun authorize(actorId: Uuid, tenantId: Uuid, operation: Operation) {
        if (!authorizer.authorize(actorId, tenantId, operation).authorized) throw AuthorizationDeniedException()
    }

    private suspend fun findContact(tenantId: Uuid, contactId: Uuid) =
        contactAccess.getContact(tenantId, contactId) ?: throw ContactNotFoundException()

    private suspend fun saveContact(tenantId: Uuid, contact: netvaerke.access.contact.Contact) {
        if (!contactAccess.saveContact(tenantId, contact)) throw ContactNotFoundException()
    }
}

class AuthorizationDeniedException : SecurityException("Actor is not authorized for this operation")

class ContactNotFoundException : NoSuchElementException("Contact not found")

private fun CreateNewContactDto.validateDetailPolicy() {
    require(emails.count { it.isPrimary } <= 1) { "A contact may have at most one primary email address" }
}

private fun UpdateContactDto.validateDetailPolicy() {
    require(emails.count { it.isPrimary } <= 1) { "A contact may have at most one primary email address" }
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
