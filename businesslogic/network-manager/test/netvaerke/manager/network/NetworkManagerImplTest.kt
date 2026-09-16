package netvaerke.manager.network

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import netvaerke.access.contact.Contact
import netvaerke.access.contact.ContactAccess
import netvaerke.access.contact.ContactImage
import netvaerke.access.contact.EmailAddress
import netvaerke.access.contact.Note
import netvaerke.access.contact.WorkInfo
import netvaerke.access.engagement.CancelFollowUpResult
import netvaerke.access.engagement.ChangeFollowUpCadenceResult
import netvaerke.access.engagement.CompleteFollowUpResult
import netvaerke.access.engagement.EngagementAccess
import netvaerke.access.engagement.FollowUp
import netvaerke.access.engagement.FollowUpCadence
import netvaerke.access.engagement.Interaction
import netvaerke.access.engagement.RegisterFollowUp
import netvaerke.access.engagement.RegisterFollowUpResult
import netvaerke.access.engagement.RescheduleFollowUpResult
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
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, contactAccess, RecordingEngagementAccess())

        assertEquals(
            TenantContactListItemDto(contactId, "Ada Lovelace", "ada@example.test", ContactImageDto("first-image")),
            manager.getTenantContacts(tenantId, actorId).single(),
        )
        assertEquals(
            TenantContactDto(
                contactId = contactId,
                name = "Ada Lovelace",
                emails = listOf(
                    EmailAddressDto("ada@example.test", isPrimary = true),
                    EmailAddressDto("ada@work.test", isPrimary = true),
                ),
                phoneNumbers = emptyList(),
                workInfo = WorkInfoDto(title = "Programmer"),
                note = NoteDto("First note"),
                image = ContactImageDto("first-image"),
            ),
            manager.getContact(tenantId, actorId, contactId),
        )
    }

    @Test
    fun `denies mutations before contact access`() = runBlocking {
        val contactAccess = RecordingContactAccess()
        val manager = NetworkManagerImpl(DenyingAuthorizationEngine, contactAccess, RecordingEngagementAccess())

        assertFailsWith<AuthorizationDeniedException> {
            manager.createNewContact(randomUuid(), randomUuid(), contactDetails())
        }

        assertTrue(contactAccess.contacts.isEmpty())
    }

    @Test
    fun `returns no contact when reads are unauthorized or contact is absent`() = runBlocking {
        val tenantId = randomUuid()
        val actorId = randomUuid()

        assertNull(
            NetworkManagerImpl(DenyingAuthorizationEngine, RecordingContactAccess(), RecordingEngagementAccess())
                .getContact(tenantId, actorId, randomUuid()),
        )
        assertNull(
            NetworkManagerImpl(AllowingAuthorizationEngine, RecordingContactAccess(), RecordingEngagementAccess())
                .getContact(tenantId, actorId, randomUuid()),
        )
        assertNull(
            NetworkManagerImpl(DenyingAuthorizationEngine, RecordingContactAccess(), RecordingEngagementAccess())
                .getContactOverview(tenantId, actorId, randomUuid()),
        )
        assertNull(
            NetworkManagerImpl(AllowingAuthorizationEngine, RecordingContactAccess(), RecordingEngagementAccess())
                .getContactOverview(tenantId, actorId, randomUuid()),
        )
    }

    @Test
    fun `rejects multiple primary email addresses`() = runBlocking {
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, RecordingContactAccess(), RecordingEngagementAccess())

        val failure = assertFailsWith<IllegalArgumentException> {
            manager.createNewContact(
                randomUuid(),
                randomUuid(),
                contactDetails(emails = listOf(
                    EmailAddressDto("first@example.test", isPrimary = true),
                    EmailAddressDto("second@example.test", isPrimary = true),
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
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, contactAccess, RecordingEngagementAccess())

        val upload = manager.reserveContactImageUpload(tenantId, actorId, contactId)
        val formerImage = manager.setContactImage(tenantId, actorId, contactId, upload.fileKey)

        assertTrue(upload.fileKey.startsWith("tenants/$tenantId/contacts/$contactId/images/"))
        assertEquals(ContactImageUpdateDto("former-key"), formerImage)
        assertEquals(ContactImageDto(upload.fileKey), manager.getContact(tenantId, actorId, contactId)?.image)
    }

    @Test
    fun `returns not found for missing contact mutations`() = runBlocking {
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, RecordingContactAccess(), RecordingEngagementAccess())

        assertFailsWith<ContactNotFoundException> {
            manager.updateContact(randomUuid(), randomUuid(), randomUuid(), updateContactDetails())
        }
        assertFailsWith<ContactNotFoundException> {
            manager.deleteContact(randomUuid(), randomUuid(), randomUuid())
        }
        Unit
    }
    @Test
    fun `manages contact interactions through the contact overview`() = runBlocking {
        val tenantId = randomUuid()
        val actorId = randomUuid()
        val contactId = randomUuid()
        val otherContactId = randomUuid()
        val contactAccess = RecordingContactAccess().apply {
            save(tenantId, Contact(contactId, "Ada Lovelace", emptyList()))
            save(tenantId, Contact(otherContactId, "Grace Hopper", emptyList()))
        }
        val manager = NetworkManagerImpl(AllowingAuthorizationEngine, contactAccess, RecordingEngagementAccess())

        val registered = manager.registerContactInteraction(
            tenantId,
            actorId,
            contactId,
            CreateContactInteractionDto(
                channel = InteractionChannelDto.EMAIL,
                notes = "Sent a follow-up.",
                occurredAt = "2026-09-09T10:00:00Z",
            ),
        )

        assertEquals(actorId, registered.recordedByUserId)
        assertEquals("2026-09-09T10:00:00Z", registered.occurredAt)
        assertEquals(
            listOf(registered),
            manager.getContactOverview(tenantId, actorId, contactId)?.interactions,
        )

        manager.updateContactInteraction(
            tenantId,
            actorId,
            contactId,
            registered.interactionId,
            UpdateContactInteractionDto(
                channel = InteractionChannelDto.PHONE,
                notes = "Discussed the proposal.",
                occurredAt = "2026-09-10T11:30:00Z",
            ),
        )

        assertEquals(
            InteractionChannelDto.PHONE,
            manager.getContactOverview(tenantId, actorId, contactId)?.interactions?.single()?.channel,
        )

        val otherContactInteraction = manager.registerContactInteraction(
            tenantId,
            actorId,
            otherContactId,
            CreateContactInteractionDto(
                channel = InteractionChannelDto.CHAT,
                notes = null,
                occurredAt = "2026-09-11T12:00:00Z",
            ),
        )
        assertFailsWith<ContactInteractionNotFoundException> {
            manager.removeContactInteraction(tenantId, actorId, contactId, otherContactInteraction.interactionId)
        }

        manager.removeContactInteraction(tenantId, actorId, contactId, registered.interactionId)

        assertEquals(emptyList(), manager.getContactOverview(tenantId, actorId, contactId)?.interactions)
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

private fun contactDetails(emails: List<EmailAddressDto> = emptyList()): CreateNewContactDto = CreateNewContactDto(
    name = "Ada Lovelace",
    emails = emails,
    phoneNumbers = emptyList(),
    workInfo = null,
    note = null,
)

private class RecordingEngagementAccess : EngagementAccess {
    private val interactions = mutableMapOf<Pair<Uuid, Uuid>, Interaction>()

    override suspend fun registerInteraction(tenantId: Uuid, interaction: Interaction): Boolean {
        val key = tenantId to interaction.id
        if (key in interactions) return false
        interactions[key] = interaction.copy(createdAt = Instant.parse("2026-09-09T00:00:00Z"))
        return true
    }

    override suspend fun updateInteraction(tenantId: Uuid, interaction: Interaction): Boolean {
        val key = tenantId to interaction.id
        val existing = interactions[key] ?: return false
        interactions[key] = existing.copy(
            channel = interaction.channel,
            notes = interaction.notes,
            occurredAt = interaction.occurredAt,
        )
        return true
    }

    override suspend fun deleteInteraction(tenantId: Uuid, interactionId: Uuid): Boolean =
        interactions.remove(tenantId to interactionId) != null

    override suspend fun getInteraction(tenantId: Uuid, interactionId: Uuid): Interaction? =
        interactions[tenantId to interactionId]

    override suspend fun getResourceInteractions(tenantId: Uuid, resourceId: Uuid): List<Interaction> =
        interactions
            .filterKeys { it.first == tenantId }
            .values
            .filter { it.resourceId == resourceId }
            .sortedWith(compareByDescending<Interaction> { it.occurredAt }.thenByDescending { it.id.toString() })

    override suspend fun registerFollowUp(
        tenantId: Uuid,
        followUp: RegisterFollowUp,
    ): RegisterFollowUpResult = error("Follow-ups are not used by these tests")

    override suspend fun getFollowUp(tenantId: Uuid, followUpId: Uuid): FollowUp? =
        error("Follow-ups are not used by these tests")

    override suspend fun getResourceFollowUps(tenantId: Uuid, resourceId: Uuid): List<FollowUp> =
        error("Follow-ups are not used by these tests")

    override suspend fun getOpenFollowUpsDueBy(tenantId: Uuid, dueOn: LocalDate): List<FollowUp> =
        error("Follow-ups are not used by these tests")

    override suspend fun rescheduleFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        dueOn: LocalDate,
    ): RescheduleFollowUpResult = error("Follow-ups are not used by these tests")

    override suspend fun changeFollowUpCadence(
        tenantId: Uuid,
        followUpId: Uuid,
        cadence: FollowUpCadence,
    ): ChangeFollowUpCadenceResult = error("Follow-ups are not used by these tests")

    override suspend fun completeFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
        completedOn: LocalDate,
    ): CompleteFollowUpResult = error("Follow-ups are not used by these tests")

    override suspend fun cancelFollowUp(
        tenantId: Uuid,
        followUpId: Uuid,
    ): CancelFollowUpResult = error("Follow-ups are not used by these tests")
}

private fun updateContactDetails(): UpdateContactDto = UpdateContactDto(
    name = "Ada Lovelace",
    emails = emptyList(),
    phoneNumbers = emptyList(),
    workInfo = null,
    note = null,
)

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
