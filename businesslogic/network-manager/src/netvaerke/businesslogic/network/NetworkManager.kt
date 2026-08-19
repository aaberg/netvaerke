package netvaerke.businesslogic.network

import kotlin.uuid.Uuid

interface NetworkManager {

    suspend fun getTenantContacts(tenantId: Uuid, actorId: Uuid): List<TenantContactListItemDto>

    suspend fun getContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid): TenantContactDto?

    suspend fun createNewContact(tenantId: Uuid, actorId: Uuid, createNewContactDto: CreateNewContactDto) : TenantContactDto

    suspend fun updateContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid, updateContactDto: UpdateContactDto)

    suspend fun deleteContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid)

}