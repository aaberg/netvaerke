package netvaerke.manager.network

import kotlin.uuid.Uuid

interface NetworkManager {

    suspend fun getTenantContacts(tenantId: Uuid, actorId: Uuid): List<TenantContactListItemDto>

    suspend fun getContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid): TenantContactDto?

    suspend fun getContactOverview(tenantId: Uuid, actorId: Uuid, contactId: Uuid): ContactOverviewDto?

    suspend fun createNewContact(tenantId: Uuid, actorId: Uuid, createNewContactDto: CreateNewContactDto) : TenantContactDto

    suspend fun updateContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid, updateContactDto: UpdateContactDto)

    suspend fun deleteContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid)

    suspend fun registerContactInteraction(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        interaction: CreateContactInteractionDto,
    ): ContactInteractionDto

    suspend fun updateContactInteraction(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        interactionId: Uuid,
        interaction: UpdateContactInteractionDto,
    )

    suspend fun removeContactInteraction(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        interactionId: Uuid,
    )

    suspend fun reserveContactImageUpload(tenantId: Uuid, actorId: Uuid, contactId: Uuid): ContactImageUploadDto

    suspend fun setContactImage(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        fileKey: String?,
    ): ContactImageUpdateDto

}
