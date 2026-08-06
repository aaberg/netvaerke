package nettvaerke.access.contact

import kotlin.uuid.Uuid

interface ContactAccess {

    fun saveContact(contact: Contact)
    fun deleteContact(contact: Contact)
    fun getContact(id: Uuid): Contact?
    fun getContacts(tenantId: Uuid): List<Contact>
}