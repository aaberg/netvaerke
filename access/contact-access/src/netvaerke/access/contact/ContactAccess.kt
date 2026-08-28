package netvaerke.access.contact

import kotlin.uuid.Uuid

interface ContactAccess {

    suspend fun saveContact(tenantId: Uuid, contact: Contact): Boolean
    suspend fun deleteContact(tenantId: Uuid, contactId: Uuid): Boolean
    suspend fun getContact(tenantId: Uuid, id: Uuid): Contact?
    suspend fun getContacts(tenantId: Uuid): List<Contact>
}
