package netvaerke.access.contact

import kotlin.uuid.Uuid

interface ContactAccess {

    fun saveContact(tenantId: Uuid, contact: Contact)
    fun deleteContact(tenantId: Uuid, contact: Contact)
    fun getContact(tenantId: Uuid, id: Uuid): Contact?
    fun getContacts(tenantId: Uuid): List<Contact>
}