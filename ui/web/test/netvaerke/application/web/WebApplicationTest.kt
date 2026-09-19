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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import netvaerke.manager.network.ContactImageDto
import netvaerke.manager.network.ContactImageUpdateDto
import netvaerke.manager.network.ContactImageUploadDto
import netvaerke.manager.network.ContactInteractionDto
import netvaerke.manager.network.ContactNotFoundException
import netvaerke.manager.network.ContactOverviewDto
import netvaerke.manager.network.ContactFollowUpCadenceDto
import netvaerke.manager.network.ContactFollowUpCompletionDto
import netvaerke.manager.network.ContactFollowUpStatusDto
import netvaerke.manager.network.ContactFollowUpFrequencyDto
import netvaerke.manager.network.ContactFollowUpIntervalUnitDto
import netvaerke.manager.network.CompleteContactFollowUpDto
import netvaerke.manager.network.ContactFollowUpDto
import netvaerke.manager.network.DueContactFollowUpDto
import netvaerke.manager.network.CreateContactFollowUpDto
import netvaerke.manager.network.CreateContactInteractionDto
import netvaerke.manager.network.CreateNewContactDto
import netvaerke.manager.network.InteractionChannelDto
import netvaerke.manager.network.NetworkManager
import netvaerke.manager.network.NoteDto
import netvaerke.manager.network.PhoneNumberDto
import netvaerke.manager.network.TenantContactDto
import netvaerke.manager.network.TenantContactListItemDto
import netvaerke.manager.network.UpdateContactInteractionDto
import netvaerke.manager.network.UpdateContactDto
import netvaerke.manager.network.WorkInfoDto
import netvaerke.manager.network.EmailAddressDto
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
    fun `shows due follow-ups grouped on the dashboard`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            dueFollowUps = listOf(
                DueContactFollowUpDto(
                    contact = TenantContactListItemDto(CONTACT_ID, "Ada Lovelace", "ada@example.test", null),
                    followUp = ContactFollowUpDto(
                        followUpId = FOLLOW_UP_ID,
                        dueOn = "2026-09-17",
                        recurrence = null,
                        status = ContactFollowUpStatusDto.OPEN,
                        completedOn = null,
                        createdAt = "2026-09-01T00:00:00Z",
                    ),
                ),
                DueContactFollowUpDto(
                    contact = TenantContactListItemDto(CONTACT_ID, "Ada Lovelace", "ada@example.test", null),
                    followUp = ContactFollowUpDto(
                        followUpId = INTERACTION_ID,
                        dueOn = "2026-09-18",
                        recurrence = ContactFollowUpCadenceDto(
                            1,
                            ContactFollowUpIntervalUnitDto.MONTHS,
                            ContactFollowUpFrequencyDto.MONTHLY,
                        ),
                        status = ContactFollowUpStatusDto.OPEN,
                        completedOn = null,
                        createdAt = "2026-09-01T00:00:00Z",
                    ),
                ),
            )
        }
        application {
            configureWebApplication(
                config(),
                membershipManager(),
                manager,
                RecordingFileStorage(),
                authenticatedSession(),
                Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC),
            )
        }

        val response = client.get("/dashboard") {
            cookie("netvaerke_timezone", "UTC")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Overdue"))
        assertTrue(body.contains("Today"))
        assertTrue(body.contains("Record interaction &amp; complete"))
        assertTrue(body.contains("Reschedule"))
        assertTrue(body.contains(">One-time<"))
        assertTrue(body.contains(">Recurring<"))
        assertTrue(body.contains(">Monthly<"))
        assertEquals("2026-09-25", manager.lastFollowUpDueOn)
    }

    @Test
    fun `separates active follow-ups and replaces an absent recurring series with its scheduler`() = testApplication {
        val contact = TenantContactDto(CONTACT_ID, "Ada Lovelace", emptyList(), emptyList(), null, null, null)
        val oneTime = ContactFollowUpDto(
            followUpId = FOLLOW_UP_ID,
            dueOn = "2026-10-01",
            recurrence = null,
            status = ContactFollowUpStatusDto.OPEN,
            completedOn = null,
            createdAt = "2026-09-01T00:00:00Z",
        )
        val recurring = ContactFollowUpDto(
            followUpId = INTERACTION_ID,
            dueOn = "2026-10-18",
            recurrence = ContactFollowUpCadenceDto(
                1,
                ContactFollowUpIntervalUnitDto.MONTHS,
                ContactFollowUpFrequencyDto.MONTHLY,
            ),
            status = ContactFollowUpStatusDto.OPEN,
            completedOn = null,
            createdAt = "2026-09-01T00:00:00Z",
        )
        val completed = ContactFollowUpDto(
            followUpId = Uuid.parse("00000000-0000-0000-0000-000000000007"),
            dueOn = "2024-01-01",
            recurrence = null,
            status = ContactFollowUpStatusDto.DONE,
            completedOn = "2024-01-02",
            createdAt = "2023-12-01T00:00:00Z",
        )
        val manager = RecordingNetworkManager().apply {
            overview = ContactOverviewDto(contact, emptyList(), listOf(oneTime, recurring, completed))
        }
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val activeResponse = client.get("/contacts/$CONTACT_ID")

        assertEquals(HttpStatusCode.OK, activeResponse.status)
        val activeBody = activeResponse.bodyAsText()
        assertTrue(activeBody.contains("Recurring follow-up"))
        assertTrue(activeBody.contains("One-time follow-ups"))
        assertTrue(activeBody.contains("Monthly"))
        assertTrue(activeBody.contains("One-time"))
        assertTrue(activeBody.contains("+ Add one-time follow-up"))
        assertTrue(activeBody.contains("Change frequency"))
        assertFalse(activeBody.contains("How often do you want to follow up with this contact?"))
        assertFalse(activeBody.contains("+ Schedule follow-up"))
        assertFalse(activeBody.contains("History ("))
        assertFalse(activeBody.contains("1 Jan 2024"))

        manager.overview = ContactOverviewDto(contact, emptyList(), listOf(oneTime, completed))
        val noRecurringResponse = client.get("/contacts/$CONTACT_ID")

        assertEquals(HttpStatusCode.OK, noRecurringResponse.status)
        val noRecurringBody = noRecurringResponse.bodyAsText()
        assertTrue(noRecurringBody.contains("How often do you want to follow up with this contact?"))
        assertTrue(noRecurringBody.contains("Schedule follow-up"))
        assertFalse(noRecurringBody.contains("Change frequency"))
    }



    @Test
    fun `validates one-time and recurring scheduling separately`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            overview = ContactOverviewDto(
                contact = TenantContactDto(CONTACT_ID, "Ada Lovelace", emptyList(), emptyList(), null, null, null),
                interactions = emptyList(),
                followUps = emptyList(),
            )
        }
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val form = client.get("/contacts/$CONTACT_ID")
        val csrfToken = assertNotNull(
            Regex("name=\"csrfToken\" value=\"([^\"]+)\"").find(form.bodyAsText())?.groupValues?.get(1),
        )
        suspend fun schedule(recurrence: String, dueOn: String? = null, frequency: String? = null) =
            client.post("/contacts/$CONTACT_ID/follow-ups") {
                cookie("netvaerke_csrf", csrfToken)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("csrfToken", csrfToken)
                            append("timeZone", "Europe/Copenhagen")
                            append("recurrence", recurrence)
                            dueOn?.let { append("dueOn", it) }
                            frequency?.let { append("frequency", it) }
                        },
                    ),
                )
            }

        assertEquals(HttpStatusCode.OK, schedule("NONE").status)
        assertNull(manager.createdFollowUp)
        assertEquals(HttpStatusCode.OK, schedule("RECURRING", frequency = "FORTNIGHTLY").status)
        assertNull(manager.createdFollowUp)

        val recurring = schedule("RECURRING", frequency = "MONTHLY")
        assertEquals(HttpStatusCode.Found, recurring.status)
        assertEquals("/contacts/$CONTACT_ID?followUpCreated=true", recurring.headers[HttpHeaders.Location])

        val oneTime = schedule("NONE", dueOn = "2026-10-01", frequency = "IGNORED")
        assertEquals(HttpStatusCode.Found, oneTime.status)
        assertEquals("/contacts/$CONTACT_ID?followUpCreated=true", oneTime.headers[HttpHeaders.Location])
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
                followUps = emptyList(),
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
        assertTrue(overview.bodyAsText().contains("contact-edit-icon"))
        assertTrue(overview.bodyAsText().contains("aria-label=\"Edit contact\""))
        assertTrue(overview.bodyAsText().contains("action=\"/contacts/$CONTACT_ID/delete\""))
        assertTrue(overview.bodyAsText().contains("data-contact-delete-form"))
        assertTrue(overview.bodyAsText().contains("button button-danger contact-action-button"))
        assertTrue(overview.bodyAsText().contains("contact-delete-icon"))
        assertTrue(overview.bodyAsText().contains("aria-label=\"Delete contact\""))
        assertTrue(overview.bodyAsText().contains("/assets/contact-delete.js"))
    }

    @Test
    fun `deletes a contact from the overview and reports an already deleted contact`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            overview = ContactOverviewDto(
                contact = TenantContactDto(CONTACT_ID, "Ada Lovelace", emptyList(), emptyList(), null, null, null),
                interactions = emptyList(),
                followUps = emptyList(),
            )
        }
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val overview = client.get("/contacts/$CONTACT_ID")
        val csrfToken = assertNotNull(
            Regex("name=\"csrfToken\" value=\"([^\"]+)\"").find(overview.bodyAsText())?.groupValues?.get(1),
        )
        val response = client.post("/contacts/$CONTACT_ID/delete") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(FormDataContent(Parameters.build { append("csrfToken", csrfToken) }))
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/dashboard", response.headers[HttpHeaders.Location])
        assertEquals(PERSONAL_TENANT_ID, manager.lastTenantId)
        assertEquals(USER_ID, manager.lastActorId)
        assertEquals(CONTACT_ID, manager.deletedContactId)

        manager.deleteFailure = ContactNotFoundException()
        val repeatedResponse = client.post("/contacts/$CONTACT_ID/delete") {
            cookie("netvaerke_csrf", csrfToken)
            setBody(FormDataContent(Parameters.build { append("csrfToken", csrfToken) }))
        }

        assertEquals(HttpStatusCode.NotFound, repeatedResponse.status)
    }

    @Test
    fun `rejects contact deletion without a valid csrf token`() = testApplication {
        val manager = RecordingNetworkManager()
        application {
            configureWebApplication(config(), membershipManager(), manager, RecordingFileStorage(), authenticatedSession())
        }

        val response = client.post("/contacts/$CONTACT_ID/delete") {
            setBody(FormDataContent(Parameters.build { append("csrfToken", "invalid") }))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(null, manager.deletedContactId)
    }

    @Test
    fun `registers updates and removes contact interactions from the overview`() = testApplication {
        val manager = RecordingNetworkManager().apply {
            overview = ContactOverviewDto(
                contact = TenantContactDto(CONTACT_ID, "Ada Lovelace", emptyList(), emptyList(), null, null, null),
                interactions = emptyList(),
                followUps = emptyList(),
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
        var dueFollowUps: List<DueContactFollowUpDto> = emptyList()
        var lastFollowUpDueOn: String? = null
        var createdFollowUp: CreateContactFollowUpDto? = null
        var followUpContactId: Uuid? = null
        var rescheduledFollowUpDueOn: String? = null
        var changedFollowUpFrequency: ContactFollowUpFrequencyDto? = null
        var completedFollowUpId: Uuid? = null
        var completedFollowUpRequest: CompleteContactFollowUpDto? = null
        var cancelledFollowUpId: Uuid? = null
        var createdContact: CreateNewContactDto? = null
        var updatedContact: UpdateContactDto? = null
        var createdInteraction: CreateContactInteractionDto? = null
        var updatedInteraction: UpdateContactInteractionDto? = null
        var interactionContactId: Uuid? = null
        var removedInteractionId: Uuid? = null
        var deletedContactId: Uuid? = null
        var deleteFailure: Exception? = null
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

        override suspend fun getOpenContactFollowUpsDueBy(
            tenantId: Uuid,
            actorId: Uuid,
            dueOn: String,
        ): List<DueContactFollowUpDto> {
            lastTenantId = tenantId
            lastActorId = actorId
            lastFollowUpDueOn = dueOn
            return dueFollowUps
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

        override suspend fun deleteContact(tenantId: Uuid, actorId: Uuid, contactId: Uuid) {
            lastTenantId = tenantId
            lastActorId = actorId
            deletedContactId = contactId
            deleteFailure?.let { throw it }
        }

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

        override suspend fun registerContactFollowUp(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            request: CreateContactFollowUpDto,
        ): ContactFollowUpDto {
            lastTenantId = tenantId
            lastActorId = actorId
            followUpContactId = contactId
            createdFollowUp = request
            return ContactFollowUpDto(
                followUpId = FOLLOW_UP_ID,
                dueOn = "2026-10-01",
                recurrence = null,
                status = ContactFollowUpStatusDto.OPEN,
                completedOn = null,
                createdAt = "2026-09-18T00:00:00Z",
            )
        }

        override suspend fun rescheduleContactFollowUp(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            followUpId: Uuid,
            dueOn: String,
        ): ContactFollowUpDto {
            lastTenantId = tenantId
            lastActorId = actorId
            followUpContactId = contactId
            rescheduledFollowUpDueOn = dueOn
            return ContactFollowUpDto(followUpId, dueOn, null, ContactFollowUpStatusDto.OPEN, null, "2026-09-18T00:00:00Z")
        }

        override suspend fun changeContactFollowUpFrequency(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            followUpId: Uuid,
            frequency: ContactFollowUpFrequencyDto,
        ): ContactFollowUpDto {
            lastTenantId = tenantId
            lastActorId = actorId
            followUpContactId = contactId
            changedFollowUpFrequency = frequency
            return ContactFollowUpDto(
                followUpId,
                "2026-09-18",
                null,
                ContactFollowUpStatusDto.OPEN,
                null,
                "2026-09-18T00:00:00Z",
            )
        }

        override suspend fun completeContactFollowUp(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            followUpId: Uuid,
            request: CompleteContactFollowUpDto,
        ): ContactFollowUpCompletionDto {
            lastTenantId = tenantId
            lastActorId = actorId
            followUpContactId = contactId
            completedFollowUpId = followUpId
            completedFollowUpRequest = request
            return ContactFollowUpCompletionDto(
                completed = ContactFollowUpDto(
                    followUpId,
                    "2026-09-18",
                    null,
                    ContactFollowUpStatusDto.DONE,
                    "2026-09-18",
                    "2026-09-18T00:00:00Z",
                ),
                next = null,
            )
        }

        override suspend fun cancelContactFollowUp(
            tenantId: Uuid,
            actorId: Uuid,
            contactId: Uuid,
            followUpId: Uuid,
        ): ContactFollowUpDto {
            lastTenantId = tenantId
            lastActorId = actorId
            followUpContactId = contactId
            cancelledFollowUpId = followUpId
            return ContactFollowUpDto(
                followUpId,
                "2026-09-18",
                null,
                ContactFollowUpStatusDto.CANCELLED,
                null,
                "2026-09-18T00:00:00Z",
            )
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
        val FOLLOW_UP_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000006")
    }
}
