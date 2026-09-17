package netvaerke.manager.network

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import netvaerke.access.contact.ContactAccess
import netvaerke.access.contact.ContactImage
import netvaerke.access.engagement.CancelFollowUpResult
import netvaerke.access.engagement.ChangeFollowUpCadenceResult
import netvaerke.access.engagement.CompleteFollowUpResult
import netvaerke.access.engagement.EngagementAccess
import netvaerke.access.engagement.FollowUp
import netvaerke.access.engagement.Interaction
import netvaerke.access.engagement.RegisterFollowUpResult
import netvaerke.access.engagement.RescheduleFollowUpResult
import netvaerke.engine.authorization.AuthorizationEngine
import netvaerke.engine.authorization.Operation

class NetworkManagerImpl(
    private val authorizer: AuthorizationEngine,
    private val contactAccess: ContactAccess,
    private val engagementAccess: EngagementAccess,
    private val clock: Clock = Clock.systemUTC(),
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
        authorize(actorId, tenantId, Operation.READ_CONTACTS)
        return contactAccess.getContact(tenantId, contactId)?.toDto()
    }

    override suspend fun getContactOverview(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
    ): ContactOverviewDto? {
        authorize(actorId, tenantId, Operation.READ_CONTACTS)
        val contact = contactAccess.getContact(tenantId, contactId) ?: return null
        return ContactOverviewDto(
            contact = contact.toDto(),
            interactions = engagementAccess.getResourceInteractions(tenantId, contactId).map(Interaction::toDto),
            followUps = engagementAccess.getResourceFollowUps(tenantId, contactId).map(FollowUp::toDto),
        )
    }

    override suspend fun getOpenContactFollowUpsDueBy(
        tenantId: Uuid,
        actorId: Uuid,
        dueOn: String,
    ): List<DueContactFollowUpDto> {
        authorize(actorId, tenantId, Operation.READ_CONTACTS)
        val contactsById = contactAccess.getContacts(tenantId).associateBy { it.id }
        return engagementAccess
            .getOpenFollowUpsDueBy(tenantId, dueOn.toLocalDate("Follow-up due date"))
            .map { followUp ->
                val contact = checkNotNull(contactsById[followUp.resourceId]) {
                    "Follow-up ${followUp.id} references missing contact ${followUp.resourceId}"
                }
                DueContactFollowUpDto(
                    contact = contact.toListItemDto(),
                    followUp = followUp.toDto(),
                )
            }
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

    override suspend fun registerContactInteraction(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        interaction: CreateContactInteractionDto,
    ): ContactInteractionDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        val newInteraction = interaction.toInteraction(contactId, actorId, randomUuid())
        check(engagementAccess.registerInteraction(tenantId, newInteraction)) { "Generated interaction ID already exists" }
        return checkNotNull(engagementAccess.getInteraction(tenantId, newInteraction.id)).toDto()
    }

    override suspend fun updateContactInteraction(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        interactionId: Uuid,
        interaction: UpdateContactInteractionDto,
    ) {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        val existingInteraction = findContactInteraction(tenantId, contactId, interactionId)
        if (!engagementAccess.updateInteraction(tenantId, existingInteraction.update(interaction))) {
            throw ContactInteractionNotFoundException()
        }
    }

    override suspend fun removeContactInteraction(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        interactionId: Uuid,
    ) {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        findContactInteraction(tenantId, contactId, interactionId)
        if (!engagementAccess.deleteInteraction(tenantId, interactionId)) {
            throw ContactInteractionNotFoundException()
        }
    }

    override suspend fun registerContactFollowUp(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        request: CreateContactFollowUpDto,
    ): ContactFollowUpDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        repeat(ID_GENERATION_ATTEMPTS) {
            when (
                val result = engagementAccess.registerFollowUp(
                    tenantId,
                    request.toRegistration(contactId, newFollowUpId()),
                )
            ) {
                is RegisterFollowUpResult.Registered -> return result.followUp.toDto()
                RegisterFollowUpResult.IdAlreadyExists -> Unit
                RegisterFollowUpResult.ActiveRecurrenceAlreadyExists ->
                    throw ActiveContactFollowUpRecurrenceException()
            }
        }
        error("Could not generate a unique follow-up ID")
    }

    override suspend fun rescheduleContactFollowUp(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        followUpId: Uuid,
        dueOn: String,
    ): ContactFollowUpDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        findContactFollowUp(tenantId, contactId, followUpId)
        return when (
            val result = engagementAccess.rescheduleFollowUp(
                tenantId,
                followUpId,
                dueOn.toLocalDate("Follow-up due date"),
            )
        ) {
            is RescheduleFollowUpResult.Rescheduled -> result.followUp.toDto()
            RescheduleFollowUpResult.NotFound -> throw ContactFollowUpNotFoundException()
            RescheduleFollowUpResult.NotOpen -> throw ContactFollowUpNotOpenException()
        }
    }

    override suspend fun changeContactFollowUpCadence(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        followUpId: Uuid,
        cadence: ContactFollowUpCadenceDto,
    ): ContactFollowUpDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        findContactFollowUp(tenantId, contactId, followUpId)
        return when (
            val result = engagementAccess.changeFollowUpCadence(tenantId, followUpId, cadence.toCadence())
        ) {
            is ChangeFollowUpCadenceResult.Changed -> result.followUp.toDto()
            ChangeFollowUpCadenceResult.NotFound -> throw ContactFollowUpNotFoundException()
            ChangeFollowUpCadenceResult.NotOpen -> throw ContactFollowUpNotOpenException()
            ChangeFollowUpCadenceResult.NotRecurring -> throw ContactFollowUpNotRecurringException()
        }
    }

    override suspend fun completeContactFollowUp(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        followUpId: Uuid,
    ): ContactFollowUpCompletionDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        findContactFollowUp(tenantId, contactId, followUpId)
        return when (
            val result = engagementAccess.completeFollowUp(
                tenantId,
                followUpId,
                LocalDate.now(clock.withZone(ZoneOffset.UTC)),
            )
        ) {
            is CompleteFollowUpResult.Completed -> result.completion.toDto()
            CompleteFollowUpResult.NotFound -> throw ContactFollowUpNotFoundException()
            CompleteFollowUpResult.AlreadyDone -> throw ContactFollowUpAlreadyCompletedException()
            CompleteFollowUpResult.Cancelled -> throw ContactFollowUpCancelledException()
        }
    }

    override suspend fun cancelContactFollowUp(
        tenantId: Uuid,
        actorId: Uuid,
        contactId: Uuid,
        followUpId: Uuid,
    ): ContactFollowUpDto {
        authorize(actorId, tenantId, Operation.UPDATE_CONTACTS)
        findContact(tenantId, contactId)
        findContactFollowUp(tenantId, contactId, followUpId)
        return when (val result = engagementAccess.cancelFollowUp(tenantId, followUpId)) {
            is CancelFollowUpResult.Cancelled -> result.followUp.toDto()
            CancelFollowUpResult.NotFound -> throw ContactFollowUpNotFoundException()
            CancelFollowUpResult.AlreadyCancelled -> throw ContactFollowUpAlreadyCancelledException()
            CancelFollowUpResult.AlreadyDone -> throw ContactFollowUpAlreadyCompletedException()
        }
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

    private suspend fun findContactInteraction(
        tenantId: Uuid,
        contactId: Uuid,
        interactionId: Uuid,
    ): Interaction = engagementAccess.getInteraction(tenantId, interactionId)
        ?.takeIf { it.resourceId == contactId }
        ?: throw ContactInteractionNotFoundException()

    private suspend fun findContactFollowUp(
        tenantId: Uuid,
        contactId: Uuid,
        followUpId: Uuid,
    ): FollowUp = engagementAccess.getFollowUp(tenantId, followUpId)
        ?.takeIf { it.resourceId == contactId }
        ?: throw ContactFollowUpNotFoundException()

    private suspend fun saveContact(tenantId: Uuid, contact: netvaerke.access.contact.Contact) {
        if (!contactAccess.saveContact(tenantId, contact)) throw ContactNotFoundException()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newFollowUpId(): Uuid = Uuid.generateV7()

    private companion object {
        const val ID_GENERATION_ATTEMPTS = 3
    }
}

class AuthorizationDeniedException : SecurityException("Actor is not authorized for this operation")

class ContactNotFoundException : NoSuchElementException("Contact not found")

class ContactInteractionNotFoundException : NoSuchElementException("Contact interaction not found")

class ContactFollowUpNotFoundException : NoSuchElementException("Contact follow-up not found")

class ContactFollowUpNotOpenException : IllegalStateException("Contact follow-up is not open")

class ContactFollowUpNotRecurringException : IllegalStateException("Contact follow-up is not recurring")

class ActiveContactFollowUpRecurrenceException :
    IllegalStateException("Contact already has an active recurring follow-up")

class ContactFollowUpAlreadyCompletedException : IllegalStateException("Contact follow-up is already completed")

class ContactFollowUpAlreadyCancelledException : IllegalStateException("Contact follow-up is already cancelled")

class ContactFollowUpCancelledException : IllegalStateException("Contact follow-up is cancelled")

private fun CreateNewContactDto.validateDetailPolicy() {
    require(emails.count { it.isPrimary } <= 1) { "A contact may have at most one primary email address" }
}

private fun UpdateContactDto.validateDetailPolicy() {
    require(emails.count { it.isPrimary } <= 1) { "A contact may have at most one primary email address" }
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
