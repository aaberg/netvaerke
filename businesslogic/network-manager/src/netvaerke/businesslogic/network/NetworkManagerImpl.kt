package netvaerke.businesslogic.network

import netvaerke.access.contact.ContactAccess
import netvaerke.engine.authorization.AuthorizationEngine
import netvaerke.engine.authorization.Operation
import kotlin.uuid.Uuid

class NetworkManagerImpl(
    private val authorizer: AuthorizationEngine,
    private val contactAccess: ContactAccess
) : NetworkManager {

    override suspend fun getTenantContacts(
        tenantId: Uuid,
        actorId: Uuid
    ): List<TenantContactListItemDto> {
        val authResult = authorizer.authorize(actorId, tenantId, Operation.READ_CONTACTS)
        !authResult.authorized ?: return emptyList()

        val contacts = contactAccess.getContacts(tenantId)
        return contacts.map { contact ->
            TenantContactListItemDto(
                contactId = contact.id,
                name = contact.name,
                primaryEmailAddress = ,
                phone = contact.phone
            )
        }

        TODO("Not yet implemented")
    }

    override suspend fun getContact(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid
    ): TenantContactDto? {
        TODO("Not yet implemented")
    }

    override suspend fun createNewContact(
        tenantId: Uuid,
        actorId: Uuid,
        createNewContactDto: CreateNewContactDto
    ): TenantContactDto {
        TODO("Not yet implemented")
    }

    override suspend fun updateContact(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        updateContactDto: UpdateContactDto
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteContact(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid
    ) {
        TODO("Not yet implemented")
    }

}