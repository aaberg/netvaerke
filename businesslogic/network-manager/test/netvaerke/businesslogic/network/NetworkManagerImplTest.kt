package netvaerke.businesslogic.network

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import netvaerke.access.contact.Contact
import netvaerke.access.contact.ContactAccess
import netvaerke.access.contact.ContactImage
import netvaerke.access.contact.EmailAddress
import netvaerke.access.contact.Note
import netvaerke.access.contact.WorkInfo
import netvaerke.engine.authorization.AuthorizationEngine
import netvaerke.engine.authorization.AuthorizationResponseDto
import netvaerke.engine.authorization.Operation

class NetworkManagerImplTest {
    @Test
    fun `reads contacts using the first malformed singleton detail`() = runBlocking {
        val tenantId = randomUuid()
        val actorId = randomUuid()
        val contactId = randomUuid()
        val contactAccess = RecordingContactAccess().apply {
            save(
                tenantId,
                Contact(
                    id = contactId,
                    name = "Ada Lovelace",
                    contactDetails = listOf(
                        EmailAddress("ada@example.test", isPrimary = true),
                        EmailAddress("ada@work.test", isPrimary = true),
                        WorkInfo(title = "Programmer"),
                        WorkInfo(title = "Mathematician"),
                        Note("First note"),
                        Note("Second note"),
                        ContactImage("first-image"),
                        ContactImage("second-image"),
                    ),
                ),
            )
        }
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, contactAccess)

        assertEquals(
            TenantContactListItemDto(contactId, "Ada Lovelace", "ada@example.test", ContactImage("first-image")),
            manager.getTenantContacts(tenantId, actorId).single(),
        )
        assertEquals(
            TenantContactDto(
                contactId = contactId,
                name = "Ada Lovelace",
                emails = listOf(
                    EmailAddress("ada@example.test", isPrimary = true),
                    EmailAddress("ada@work.test", isPrimary = true),
                ),
                phoneNumbers = emptyList(),
                workInfo = WorkInfo(title = "Programmer"),
                note = Note("First note"),
                image = ContactImage("first-image"),
            ),
            manager.getContact(tenantId, actorId, contactId),
        )
    }

    @Test
    fun `denies mutations before contact access`() = runBlocking {
        val contactAccess = RecordingContactAccess()
        val manager = NetworkManagerImpl(DenyingAuthorizationEngine, contactAccess)

        assertFailsWith<AuthorizationDeniedException> {
            manager.createNewContact(randomUuid(), randomUuid(), contactDetails())
        }

        assertTrue(contactAccess.contacts.isEmpty())
    }

    @Test
    fun `returns no contact when reads are unauthorized or contact is absent`() = runBlocking {
        val tenantId = randomUuid()
        val actorId = randomUuid()

        assertNull(NetworkManagerImpl(DenyingAuthorizationEngine, RecordingContactAccess()).getContact(tenantId, actorId, randomUuid()))
        assertNull(NetworkManagerImpl(AllowingAuthorizationEngine, RecordingContactAccess()).getContact(tenantId, actorId, randomUuid()))
    }

    @Test
    fun `rejects multiple primary email addresses`() = runBlocking {
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, RecordingContactAccess())

        val failure = assertFailsWith<IllegalArgumentException> {
            manager.createNewContact(
                randomUuid(),
                randomUuid(),
                contactDetails(emails = listOf(
                    EmailAddress("first@example.test", isPrimary = true),
                    EmailAddress("second@example.test", isPrimary = true),
                )),
            )
        }

        assertEquals("A contact may have at most one primary email address", failure.message)
    }

    @Test
    fun `reserves and associates an image key while returning the former key`() = runBlocking {
        val tenantId = randomUuid()
        val actorId = randomUuid()
        val contactId = randomUuid()
        val contactAccess = RecordingContactAccess().apply {
            save(
                tenantId,
                Contact(contactId, "Ada Lovelace", listOf(ContactImage("former-key"), ContactImage("stale-key"))),
            )
        }
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, contactAccess)

        val upload = manager.reserveContactImageUpload(tenantId, actorId, contactId)
        val formerImage = manager.setContactImage(tenantId, actorId, contactId, upload.fileKey)

        assertTrue(upload.fileKey.startsWith("tenants/$tenantId/contacts/$contactId/images/"))
        assertEquals(ContactImageUpdateDto("former-key"), formerImage)
        assertEquals(ContactImage(upload.fileKey), manager.getContact(tenantId, actorId, contactId)?.image)
    }

    @Test
    fun `returns not found for missing contact mutations`() = runBlocking {
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, RecordingContactAccess())

        assertFailsWith<ContactNotFoundException> {
            manager.updateContact(randomUuid(), randomUuid(), randomUuid(), updateContactDetails())
        }
        assertFailsWith<ContactNotFoundException> {
            manager.deleteContact(randomUuid(), randomUuid(), randomUuid())
        }
        Unit
    }
}

private object AllowingAuthorizationEngine : AuthorizationEngine {
    override suspend fun authorize(actorId: Uuid, tenantId: Uuid, operation: Operation) = AuthorizationResponseDto(true)
}

private object DenyingAuthorizationEngine : AuthorizationEngine {
    override suspend fun authorize(actorId: Uuid, tenantId: Uuid, operation: Operation) = AuthorizationResponseDto(false)
}

private class RecordingContactAccess : ContactAccess {
    val contacts = mutableMapOf<Pair<Uuid, Uuid>, Contact>()

    override suspend fun saveContact(tenantId: Uuid, contact: Contact): Boolean {
        val key = tenantId to contact.id
        if (contacts.keys.any { it.second == contact.id && it != key }) return false
        contacts[key] = contact
        return true
    }

    override suspend fun deleteContact(tenantId: Uuid, contactId: Uuid): Boolean =
        contacts.remove(tenantId to contactId) != null

    override suspend fun getContact(tenantId: Uuid, id: Uuid): Contact? = contacts[tenantId to id]

    override suspend fun getContacts(tenantId: Uuid): List<Contact> =
        contacts.filterKeys { it.first == tenantId }.values.toList()

    fun save(tenantId: Uuid, contact: Contact) {
        contacts[tenantId to contact.id] = contact
    }
}

private fun contactDetails(emails: List<EmailAddress> = emptyList()): CreateNewContactDto = CreateNewContactDto(
    name = "Ada Lovelace",
    emails = emails,
    phoneNumbers = emptyList(),
    workInfo = null,
    note = null,
)

private fun updateContactDetails(): UpdateContactDto = UpdateContactDto(
    name = "Ada Lovelace",
    emails = emptyList(),
    phoneNumbers = emptyList(),
    workInfo = null,
    note = null,
)

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
