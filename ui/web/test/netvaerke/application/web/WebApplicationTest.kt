package netvaerke.application.web

import io.ktor.client.request.cookie
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.server.testing.testApplication
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import netvaerke.manager.network.ContactImageDto
import netvaerke.manager.network.ContactImageUpdateDto
import netvaerke.manager.network.ContactImageUploadDto
import netvaerke.manager.network.ContactInteractionDto
import netvaerke.manager.network.ContactOverviewDto
import netvaerke.manager.network.CreateContactInteractionDto
import netvaerke.manager.network.CreateNewContactDto
import netvaerke.manager.network.EmailAddressDto
import netvaerke.manager.network.InteractionChannelDto
import netvaerke.manager.network.NetworkManager
import netvaerke.manager.network.NoteDto
import netvaerke.manager.network.PhoneNumberDto
import netvaerke.manager.network.TenantContactDto
import netvaerke.manager.network.TenantContactListItemDto
import netvaerke.manager.network.UpdateContactInteractionDto
import netvaerke.manager.network.UpdateContactDto
import netvaerke.manager.network.WorkInfoDto
import netvaerke.manager.membership.GetProfileRequest
import netvaerke.manager.membership.GetProfileResponse
import netvaerke.manager.membership.MembershipManager
import netvaerke.manager.membership.ProfileDto
import netvaerke.manager.membership.RegisterProfileRequest
import netvaerke.manager.membership.TenantDto
import netvaerke.manager.membership.TenantTypeDto

class WebApplicationTest {
    @Test
    fun `renders the landing page`() = testApplication {
        application {
            configureWebApplication(config(), membershipManager(), RecordingNetworkManager(), RecordingFileStorage())
        }

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Make room for the people who matter."))
        assertTrue(body.contains("data-theme-toggle"))
        assertTrue(body.contains("/assets/theme.js"))
    }

    @Test
    fun `renders personal tenant contacts on the dashboard`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            contacts = listOf(TenantContactListItemDto(CONTACT_ID, "Ada Lovelace", "ada@example.test", null))
        }
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val response = client.get("/dashboard")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Ada Lovelace"))
        assertEquals(PERSONAL_TENANT_ID, manager.lastTenantId)
        assertEquals(USER_ID, manager.lastActorId)
    }


    @Test
    fun `opens a contact overview while retaining a direct edit link`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            contacts = listOf(TenantContactListItemDto(CONTACT_ID, "Ada Lovelace", "ada@example.test", null))
            overview = ContactOverviewDto(
                contact = TenantContactDto(
                    contactId = CONTACT_ID,
                    name = "Ada Lovelace",
                    emails = listOf(EmailAddressDto("ada@example.test", isPrimary = true, label = "Work")),
                    phoneNumbers = emptyList(),
                    workInfo = WorkInfoDto("Programmer", "Analytical Engine"),
                    note = NoteDto("Met at the salon."),
                    image = null,
                ),
                interactions = listOf(
                    ContactInteractionDto(
                        interactionId = INTERACTION_ID,
                        recordedByUserId = USER_ID,
                        channel = InteractionChannelDto.EMAIL,
                        notes = "Sent an introduction.",
                        occurredAt = "2025-01-02T15:04:05Z",
                        createdAt = "2025-01-02T15:05:00Z",
                    ),
                ),
            )
        }
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val dashboard = client.get("/dashboard")
        val overview = client.get("/contacts/$CONTACT_ID")

        assertEquals(HttpStatusCode.OK, dashboard.status)
        assertTrue(dashboard.bodyAsText().contains("href=\"/contacts/$CONTACT_ID\""))
        assertTrue(dashboard.bodyAsText().contains("href=\"/contacts/$CONTACT_ID/edit\""))
        assertEquals(HttpStatusCode.OK, overview.status)
        assertTrue(overview.bodyAsText().contains("Met at the salon."))
        assertTrue(overview.bodyAsText().contains("Sent an introduction."))
        assertTrue(overview.bodyAsText().contains("Record an interaction"))
        assertTrue(overview.bodyAsText().contains("interaction-icon-email"))
        assertTrue(overview.bodyAsText().contains("data-interaction-local-time"))
        assertTrue(overview.bodyAsText().contains("<details class=\"new-interaction\">"))
        assertTrue(overview.bodyAsText().contains("new-interaction-trigger"))
        assertEquals(2, "data-interaction-cancel".toRegex().findAll(overview.bodyAsText()).count())
        assertTrue(overview.bodyAsText().contains("data-interaction-remove"))
        assertTrue(overview.bodyAsText().contains("aria-label=\"Remove interaction\""))
    }

    @Test
    fun `registers updates and removes contact interactions from the overview`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            overview = ContactOverviewDto(
                contact = TenantContactDto(CONTACT_ID, "Ada Lovelace", emptyList(), emptyList(), null, null, null),
                interactions = emptyList(),
            )
        }
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val form = client.get("/contacts/$CONTACT_ID")
        val csrfToken = assertNotNull(
            Regex("name=\"csrfToken\" value=\"([^\"]+)\"").find(form.bodyAsText())?.groupValues?.get(1),
        )
        val createResponse = client.post("/contacts/$CONTACT_ID/interactions") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("csrfToken", csrfToken)
                        append("channel", "PHONE")
                        append("notes", "Talked about the project.")
                        append("occurredAt", "2025-01-02T15:04:05Z")
                    },
                ),
            )
        }
        val updateResponse = client.post("/contacts/$CONTACT_ID/interactions/$INTERACTION_ID") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("csrfToken", csrfToken)
                        append("channel", "CHAT")
                        append("notes", "Agreed on next steps.")
                        append("occurredAt", "2025-01-03T15:04:05Z")
                    },
                ),
            )
        }
        val removeResponse = client.post("/contacts/$CONTACT_ID/interactions/$INTERACTION_ID/remove") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(FormDataContent(Parameters.build { append("csrfToken", csrfToken) }))
        }

        assertEquals(HttpStatusCode.Found, createResponse.status)
        assertEquals(HttpStatusCode.Found, updateResponse.status)
        assertEquals(HttpStatusCode.Found, removeResponse.status)
        assertEquals(
            CreateContactInteractionDto(
                InteractionChannelDto.PHONE,
                "Talked about the project.",
                "2025-01-02T15:04:05Z",
            ),
            manager.createdInteraction,
        )
        assertEquals(
            UpdateContactInteractionDto(
                InteractionChannelDto.CHAT,
                "Agreed on next steps.",
                "2025-01-03T15:04:05Z",
            ),
            manager.updatedInteraction,
        )
        assertEquals(CONTACT_ID, manager.interactionContactId)
        assertEquals(INTERACTION_ID, manager.removedInteractionId)
    }
    @Test
    fun `creates a contact from the form`() = testApplication {
        val manager = RecordingNetworkManager()
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val form = client.get("/contacts/new")
        val formBody = form.bodyAsText()
        val csrfToken = assertNotNull(Regex("name=\"csrfToken\" value=\"([^\"]+)\"").find(formBody)?.groupValues?.get(1))
        val response = client.post("/contacts") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("csrfToken", csrfToken)
                        append("name", "Ada Lovelace")
                        append("emailValue-0", "ada@example.test")
                        append("emailLabel-0", "Work")
                        append("emailPrimary", "0")
                        append("phoneValue-0", "+45 12 34 56 78")
                        append("phoneLabel-0", "Mobile")
                        append("workTitle", "Programmer")
                        append("workOrganization", "Analytical Engine")
                        append("note", "Met at the salon.")
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            CreateNewContactDto(
                name = "Ada Lovelace",
                emails = listOf(EmailAddressDto("ada@example.test", isPrimary = true, label = "Work")),
                phoneNumbers = listOf(PhoneNumberDto("+45 12 34 56 78", "Mobile")),
                workInfo = WorkInfoDto("Programmer", "Analytical Engine"),
                note = NoteDto("Met at the salon."),
            ),
            manager.createdContact,
        )
        assertEquals(PERSONAL_TENANT_ID, manager.lastTenantId)
        assertEquals(USER_ID, manager.lastActorId)
    }

    @Test
    fun `renders existing details for editing`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            contact = TenantContactDto(
                contactId = CONTACT_ID,
                name = "Ada Lovelace",
                emails = listOf(EmailAddressDto("ada@example.test", isPrimary = true, label = "Work")),
                phoneNumbers = listOf(PhoneNumberDto("+45 12 34 56 78", "Mobile")),
                workInfo = WorkInfoDto("Programmer", "Analytical Engine"),
                note = NoteDto("Met at the salon."),
                image = ContactImageDto("unused-image-key"),
            )
        }
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val response = client.get("/contacts/$CONTACT_ID/edit")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Met at the salon."))
        assertTrue(response.bodyAsText().contains("Analytical Engine"))
        assertTrue(response.bodyAsText().contains("https://files.example.test/netvaerke/unused-image-key"))
    }

    @Test
    fun `updates a contact from the form`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            contact = TenantContactDto(
                contactId = CONTACT_ID,
                name = "Ada Lovelace",
                emails = emptyList(),
                phoneNumbers = emptyList(),
                workInfo = null,
                note = null,
                image = null,
            )
        }
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val form = client.get("/contacts/$CONTACT_ID/edit")
        val csrfToken = assertNotNull(
            Regex("name=\"csrfToken\" value=\"([^\"]+)\"").find(form.bodyAsText())?.groupValues?.get(1),
        )
        val response = client.post("/contacts/$CONTACT_ID") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("csrfToken", csrfToken)
                        append("name", "Ada Byron")
                        append("emailValue-0", "ada@example.test")
                        append("emailPrimary", "0")
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            UpdateContactDto(
                name = "Ada Byron",
                emails = listOf(EmailAddressDto("ada@example.test", isPrimary = true)),
                phoneNumbers = emptyList(),
                workInfo = null,
                note = null,
            ),
            manager.updatedContact,
        )
        assertEquals(PERSONAL_TENANT_ID, manager.lastTenantId)
        assertEquals(USER_ID, manager.lastActorId)
    }

    @Test
    fun `uploads and associates a JPEG contact photo`() = testApplication {
        val manager = RecordingNetworkManager()
        val storage = RecordingFileStorage()
        application {
            configureWebApplication(config(), membershipManager(), manager, storage, authenticatedSession())
        }

        val form = client.get("/contacts/new")
        val csrfToken = assertNotNull(
            Regex("name=\"csrfToken\" value=\"([^\"]+)\"").find(form.bodyAsText())?.groupValues?.get(1),
        )
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val response = client.post("/contacts") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("csrfToken", csrfToken)
                        append("name", "Ada Lovelace")
                        append(
                            "image",
                            image,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=ada.jpg")
                            },
                        )
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("tenants/personal/contacts/ada/image", manager.reservedImageKey)
        assertEquals("tenants/personal/contacts/ada/image", manager.setImageFileKey)
        val upload = assertNotNull(storage.uploads.singleOrNull())
        assertEquals("netvaerke", upload.bucket)
        assertEquals("tenants/personal/contacts/ada/image", upload.fileKey)
        assertEquals("image/jpeg", upload.contentType)
        assertContentEquals(image, upload.content)
    }

    @Test
    fun `removes the current contact photo`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            contact = TenantContactDto(CONTACT_ID, "Ada Lovelace", emptyList(), emptyList(), null, null, ContactImageDto("old-image"))
            previousImageFileKey = "old-image"
        }
        val storage = RecordingFileStorage()
        application {
            configureWebApplication(config(), membershipManager(), manager, storage, authenticatedSession())
        }

        val form = client.get("/contacts/$CONTACT_ID/edit")
        val csrfToken = assertNotNull(
            Regex("name=\"csrfToken\" value=\"([^\"]+)\"").find(form.bodyAsText())?.groupValues?.get(1),
        )
        val response = client.post("/contacts/$CONTACT_ID") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("csrfToken", csrfToken)
                        append("name", "Ada Lovelace")
                        append("removeImage", "true")
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(null, manager.setImageFileKey)
        assertEquals(listOf("netvaerke" to "old-image"), storage.deletedFiles)
    }

    private fun config(): ApplicationConfig = ApplicationConfig.fromEnvironment(
        mapOf(
            "NATS_URL" to "nats://localhost:4222",
            "HANKO_API_URL" to "http://localhost:8000",
            "FILE_STORAGE_ENDPOINT" to "http://garage:3900",
            "FILE_STORAGE_REGION" to "garage",
            "FILE_STORAGE_BUCKET" to "netvaerke",
            "FILE_STORAGE_ACCESS_KEY" to "access-key",
            "FILE_STORAGE_SECRET_KEY" to "secret-key",
        ),
    )

    private fun authenticatedSession(): SessionValidator = SessionValidator {
        HankoSessionResult.Authenticated(AuthenticatedUser(USER_ID, "ada@example.test"))
    }

    private fun membershipManager(): MembershipManager = object : MembershipManager {
        override suspend fun registerProfileWithPersonalTenant(registerProfileRequest: RegisterProfileRequest) = Unit

        override suspend fun getProfile(getProfileRequest: GetProfileRequest): GetProfileResponse = GetProfileResponse(
            profile = ProfileDto(USER_ID, "Ada", "ada@example.test"),
            tenants = listOf(
                TenantDto(ORGANIZATION_TENANT_ID, TenantTypeDto.ORGANIZATION, "Babbage & Co", emptyList()),
                TenantDto(PERSONAL_TENANT_ID, TenantTypeDto.PERSONAL, "Ada", listOf(USER_ID)),
            ),
        )
    }

    private class RecordingNetworkManager : NetworkManager {
        var contacts: List<TenantContactListItemDto> = emptyList()
        var contact: TenantContactDto? = null
        var overview: ContactOverviewDto? = null
        var createdContact: CreateNewContactDto? = null
        var updatedContact: UpdateContactDto? = null
        var createdInteraction: CreateContactInteractionDto? = null
        var updatedInteraction: UpdateContactInteractionDto? = null
        var interactionContactId: Uuid? = null
        var removedInteractionId: Uuid? = null
        var reservedImageKey: String? = null
        var setImageFileKey: String? = null
        var previousImageFileKey: String? = null
        var lastTenantId: Uuid? = null
        var lastActorId: Uuid? = null

        override suspend fun getTenantContacts(tenantId: Uuid, actorId: Uuid): List<TenantContactListItemDto> {
            lastTenantId = tenantId
            lastActorId = actorId
            return contacts
        }

        override suspend fun getContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid): TenantContactDto? {
            lastTenantId = tenantId
            lastActorId = actorId
            return contact
        }

        override suspend fun getContactOverview(tenantId: Uuid, actorId: Uuid, contactId: Uuid): ContactOverviewDto? {
            lastTenantId = tenantId
            lastActorId = actorId
            return overview
        }

        override suspend fun createNewContact(
            tenantId: Uuid,
            actorId: Uuid,
            createNewContactDto: CreateNewContactDto,
        ): TenantContactDto {
            lastTenantId = tenantId
            lastActorId = actorId
            createdContact = createNewContactDto
            return TenantContactDto(
                contactId = CONTACT_ID,
                name = createNewContactDto.name,
                emails = createNewContactDto.emails,
                phoneNumbers = createNewContactDto.phoneNumbers,
                workInfo = createNewContactDto.workInfo,
                note = createNewContactDto.note,
                image = null,
            )
        }

        override suspend fun updateContact(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            updateContactDto: UpdateContactDto,
        ) {
            lastTenantId = tenantId
            lastActorId = actorId
            updatedContact = updateContactDto
        }

        override suspend fun deleteContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid) = Unit

        override suspend fun registerContactInteraction(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            interaction: CreateContactInteractionDto,
        ): ContactInteractionDto {
            lastTenantId = tenantId
            lastActorId = actorId
            interactionContactId = contactId
            createdInteraction = interaction
            return ContactInteractionDto(
                interactionId = INTERACTION_ID,
                recordedByUserId = actorId,
                channel = interaction.channel,
                notes = interaction.notes,
                occurredAt = interaction.occurredAt,
                createdAt = interaction.occurredAt,
            )
        }

        override suspend fun updateContactInteraction(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            interactionId: Uuid,
            interaction: UpdateContactInteractionDto,
        ) {
            lastTenantId = tenantId
            lastActorId = actorId
            interactionContactId = contactId
            updatedInteraction = interaction
        }

        override suspend fun removeContactInteraction(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            interactionId: Uuid,
        ) {
            lastTenantId = tenantId
            lastActorId = actorId
            interactionContactId = contactId
            removedInteractionId = interactionId
        }

        override suspend fun reserveContactImageUpload(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
        ): ContactImageUploadDto {
            lastTenantId = tenantId
            lastActorId = actorId
            return ContactImageUploadDto("tenants/personal/contacts/ada/image").also { reservedImageKey = it.fileKey }
        }

        override suspend fun setContactImage(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            fileKey: String?,
        ): ContactImageUpdateDto {
            lastTenantId = tenantId
            lastActorId = actorId
            setImageFileKey = fileKey
            return ContactImageUpdateDto(previousFileKey = previousImageFileKey)
        }
    }

    private class RecordingFileStorage : FileStorage {
        val uploads = mutableListOf<StoredUpload>()
        val deletedFiles = mutableListOf<Pair<String, String>>()

        override suspend fun putFile(
            bucket: String,
            fileKey: String,
            contentType: String,
            contentLength: Long,
            content: InputStream,
        ) {
            uploads += StoredUpload(bucket, fileKey, contentType, content.readBytes())
        }

        override fun createGetUrl(bucket: String, fileKey: String, expiry: kotlin.time.Duration): String =
            "https://files.example.test/$bucket/$fileKey"

        override suspend fun deleteFile(bucket: String, fileKey: String) {
            deletedFiles += bucket to fileKey
        }

        override fun close() = Unit
    }

    private data class StoredUpload(
        val bucket: String,
        val fileKey: String,
        val contentType: String,
        val content: ByteArray,
    )

    private companion object {
        val USER_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val PERSONAL_TENANT_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000002")
        val ORGANIZATION_TENANT_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000003")
        val CONTACT_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000004")
        val INTERACTION_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000005")
    }
}
