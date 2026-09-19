package netvaerke.application.web

import freemarker.cache.ClassTemplateLoader
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.freemarker.FreeMarkerContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.http.content.staticResources
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import netvaerke.ifx.IfxRemoteException
import netvaerke.manager.membership.GetProfileRequest
import netvaerke.manager.membership.MembershipManager
import netvaerke.manager.membership.ProfileDto
import netvaerke.manager.membership.ProfileNotFoundException
import netvaerke.manager.membership.RegisterProfileRequest
import netvaerke.manager.membership.TenantTypeDto
import netvaerke.manager.network.ActiveContactFollowUpRecurrenceException
import netvaerke.manager.network.AuthorizationDeniedException
import netvaerke.manager.network.CompleteContactFollowUpDto
import netvaerke.manager.network.ContactFollowUpAlreadyCancelledException
import netvaerke.manager.network.ContactFollowUpAlreadyCompletedException
import netvaerke.manager.network.ContactFollowUpCancelledException
import netvaerke.manager.network.ContactFollowUpCadenceDto
import netvaerke.manager.network.ContactFollowUpDto
import netvaerke.manager.network.ContactFollowUpNotFoundException
import netvaerke.manager.network.ContactFollowUpNotOpenException
import netvaerke.manager.network.ContactFollowUpNotRecurringException
import netvaerke.manager.network.ContactFollowUpStatusDto
import netvaerke.manager.network.ContactInteractionDto
import netvaerke.manager.network.ContactInteractionNotFoundException
import netvaerke.manager.network.ContactNotFoundException
import netvaerke.manager.network.ContactOverviewDto
import netvaerke.manager.network.EmailAddressDto
import netvaerke.manager.network.NetworkManager
import netvaerke.manager.network.PhoneNumberDto
import netvaerke.manager.network.TenantContactListItemDto
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

internal fun Application.configureWebApplication(
    config: ApplicationConfig,
    membershipManager: MembershipManager,
    networkManager: NetworkManager,
    fileStorage: FileStorage,
    sessionValidator: SessionValidator = HankoSessionValidator(config.hankoValidationApiUrl),
    clock: Clock = Clock.systemUTC(),
) {
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(Application::class.java.classLoader, "templates")
    }

    routing {
        staticResources("/assets", "static")

        get("/") {
            call.respondPage("landing.ftl")
        }

        get("/sign-in") {
            call.respondPage("authentication.ftl", authenticationModel(config, "login", "Sign in to netværke"))
        }

        get("/sign-up") {
            call.respondPage("authentication.ftl", authenticationModel(config, "registration", "Create your netværke account"))
        }

        get("/dashboard") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@get
            val today = LocalDate.now(clock.withZone(context.timeZone))
            val contacts = try {
                networkManager.getTenantContacts(context.tenantId, context.user.id)
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@get
            }
            val followUps = try {
                networkManager.getOpenContactFollowUpsDueBy(
                    context.tenantId,
                    context.user.id,
                    today.plusDays(DASHBOARD_FOLLOW_UP_DAYS).toString(),
                )
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@get
            }
            call.respondPage(
                "dashboard.ftl",
                mapOf(
                    "profile" to context.profile,
                    "contacts" to contacts.map { it.toContactListItem(fileStorage, config.fileStorageBucket) },
                    "followUpSections" to followUps.toDashboardFollowUpSections(
                        fileStorage,
                        config.fileStorageBucket,
                        today,
                    ),
                    "followUpMessage" to call.followUpMessage(),
                    "csrfToken" to call.csrfToken(config.secureCookies),
                    "timeZone" to context.timeZone.id,
                    "hankoApiUrl" to config.hankoApiUrl,
                    "hankoCookieDomain" to config.hankoCookieDomain,
                ),
            )
        }

        get("/onboarding") {
            val user = call.authenticatedUser(sessionValidator) ?: return@get
            when (call.loadProfile(membershipManager, user)) {
                is ProfileLookup.Found -> call.respondRedirect("/dashboard")
                ProfileLookup.Missing -> call.respondOnboarding(config, user)
                ProfileLookup.Unavailable -> call.serviceUnavailable()
            }
        }

        post("/onboarding") {
            val user = call.authenticatedUser(sessionValidator) ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val name = parameters["name"]?.trim().orEmpty()
            val email = parameters["email"]?.trim().orEmpty()
            val validationError = validateProfileDetails(name, email)
            if (validationError != null) {
                call.respondOnboarding(config, user, name, email, validationError)
                return@post
            }

            try {
                membershipManager.registerProfileWithPersonalTenant(RegisterProfileRequest(user.id, name, email))
            } catch (_: Exception) {
                call.respondOnboarding(
                    config,
                    user,
                    name,
                    email,
                    "We could not create your profile. Please try again.",
                )
                return@post
            }

            call.respondRedirect("/dashboard")
        }

        get("/contacts/new") {
            if (call.personalTenantContext(sessionValidator, membershipManager) == null) return@get
            call.respondContactForm(config, ContactForm(), "/contacts", "Add contact", "Add contact")
        }

        post("/contacts") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val submission = call.receiveContactSubmission()
            val parameters = submission.parameters
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val form = parameters.toContactForm()
            val validationError = form.validationError() ?: submission.imageError
            if (validationError != null) {
                call.respondContactForm(config, form, "/contacts", "Add contact", "Add contact", validationError)
                return@post
            }

            val contact = try {
                networkManager.createNewContact(context.tenantId, context.user.id, form.toCreateDto())
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@post
            }
            submission.image?.let { image ->
                try {
                    uploadContactImage(fileStorage, config.fileStorageBucket, networkManager, context, contact.contactId, image)
                } catch (failure: Exception) {
                    fileStorageLogger.error("Could not upload contact image for contact ${contact.contactId}", failure)
                    call.respondRedirect("/contacts/${contact.contactId}/edit?imageUpload=failed")
                    return@post
                }
            }
            call.respondRedirect("/dashboard")
        }

        get("/contacts/{contactId}") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@get
            val contactId = call.contactIdOrNotFound() ?: return@get
            val query = call.request.queryParameters
            val selectedCompleteFollowUpId = query["completeFollowUp"].parseFollowUpId()
            val selectedRescheduleFollowUpId = query["rescheduleFollowUp"].parseFollowUpId()
            val selectedFrequencyFollowUpId = query["editFrequencyFollowUp"].parseFollowUpId()
            call.respondContactOverview(
                config = config,
                networkManager = networkManager,
                fileStorage = fileStorage,
                context = context,
                contactId = contactId,
                selectedCompleteFollowUpId = selectedCompleteFollowUpId,
                selectedRescheduleFollowUpId = selectedRescheduleFollowUpId,
                selectedFrequencyFollowUpId = selectedFrequencyFollowUpId,
                followUpMode = when {
                    selectedCompleteFollowUpId != null -> FollowUpFormMode.COMPLETE_WITH_INTERACTION
                    selectedRescheduleFollowUpId != null -> FollowUpFormMode.RESCHEDULE
                    selectedFrequencyFollowUpId != null -> FollowUpFormMode.FREQUENCY
                    else -> null
                },
                followUpMessage = call.followUpMessage(),
            )
        }

        post("/contacts/{contactId}/delete") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            try {
                networkManager.deleteContact(context.tenantId, context.user.id, contactId)
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@post
            }
            call.respondRedirect("/dashboard")
        }
        post("/contacts/{contactId}/follow-ups") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val form = parameters.toContactFollowUpForm()
            val timeZone = parameters.submittedTimeZone(context.timeZone)
            val validationError = form.validationError()
                ?: if (form.recurrence == ContactFollowUpForm.RECURRENCE_RECURRING && timeZone.toZoneIdOrNull() == null) {
                    "We could not determine your local time zone. Refresh the page and try again."
                } else {
                    null
                }
            if (validationError != null) {
                call.respondContactOverview(
                    config = config,
                    networkManager = networkManager,
                    fileStorage = fileStorage,
                    context = context,
                    contactId = contactId,
                    newFollowUp = form,
                    followUpMode = FollowUpFormMode.CREATE,
                    followUpError = validationError,
                )
                return@post
            }

            try {
                networkManager.registerContactFollowUp(
                    context.tenantId,
                    context.user.id,
                    contactId,
                    form.toCreateDto(timeZone),
                )
            } catch (failure: Exception) {
                val error = failure.followUpFormError()
                if (error != null) {
                    call.respondContactOverview(
                        config = config,
                        networkManager = networkManager,
                        fileStorage = fileStorage,
                        context = context,
                        contactId = contactId,
                        newFollowUp = form,
                        followUpMode = FollowUpFormMode.CREATE,
                        followUpError = error,
                    )
                } else {
                    call.respondContactFailure(failure)
                }
                return@post
            }
            call.respondRedirect("/contacts/$contactId?followUpCreated=true")
        }

        post("/contacts/{contactId}/follow-ups/{followUpId}/reschedule") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val followUpId = call.followUpIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val dueOn = parameters["dueOn"]?.trim().orEmpty()
            val validationError = if (dueOn.toLocalDateOrNull() == null) "Enter a valid due date." else null
            if (validationError != null) {
                call.respondContactOverview(
                    config = config,
                    networkManager = networkManager,
                    fileStorage = fileStorage,
                    context = context,
                    contactId = contactId,
                    selectedRescheduleFollowUpId = followUpId,
                    rescheduleDueOn = dueOn,
                    followUpMode = FollowUpFormMode.RESCHEDULE,
                    followUpError = validationError,
                )
                return@post
            }

            try {
                networkManager.rescheduleContactFollowUp(
                    context.tenantId,
                    context.user.id,
                    contactId,
                    followUpId,
                    dueOn,
                )
            } catch (failure: Exception) {
                val error = failure.followUpFormError()
                if (error != null) {
                    call.respondContactOverview(
                        config = config,
                        networkManager = networkManager,
                        fileStorage = fileStorage,
                        context = context,
                        contactId = contactId,
                        selectedRescheduleFollowUpId = followUpId,
                        rescheduleDueOn = dueOn,
                        followUpMode = FollowUpFormMode.RESCHEDULE,
                        followUpError = error,
                    )
                } else {
                    call.respondContactFailure(failure)
                }
                return@post
            }
            call.respondRedirect("/contacts/$contactId?followUpUpdated=rescheduled")
        }

        post("/contacts/{contactId}/follow-ups/{followUpId}/frequency") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val followUpId = call.followUpIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val form = parameters.toContactFollowUpFrequencyForm()
            val validationError = form.validationError()
            if (validationError != null) {
                call.respondContactOverview(
                    config = config,
                    networkManager = networkManager,
                    fileStorage = fileStorage,
                    context = context,
                    contactId = contactId,
                    selectedFrequencyFollowUpId = followUpId,
                    frequencyForm = form,
                    followUpMode = FollowUpFormMode.FREQUENCY,
                    followUpError = validationError,
                )
                return@post
            }

            try {
                networkManager.changeContactFollowUpFrequency(
                    context.tenantId,
                    context.user.id,
                    contactId,
                    followUpId,
                    form.toDto(),
                )
            } catch (failure: Exception) {
                val error = failure.followUpFormError()
                if (error != null) {
                    call.respondContactOverview(
                        config = config,
                        networkManager = networkManager,
                        fileStorage = fileStorage,
                        context = context,
                        contactId = contactId,
                        selectedFrequencyFollowUpId = followUpId,
                        frequencyForm = form,
                        followUpMode = FollowUpFormMode.FREQUENCY,
                        followUpError = error,
                    )
                } else {
                    call.respondContactFailure(failure)
                }
                return@post
            }
            call.respondRedirect("/contacts/$contactId?followUpUpdated=frequency")
        }

        post("/contacts/{contactId}/follow-ups/{followUpId}/complete") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val followUpId = call.followUpIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val timeZone = parameters.submittedTimeZone(context.timeZone)
            if (timeZone.toZoneIdOrNull() == null) {
                call.respondContactOverview(
                    config = config,
                    networkManager = networkManager,
                    fileStorage = fileStorage,
                    context = context,
                    contactId = contactId,
                    selectedCompleteFollowUpId = followUpId,
                    followUpMode = FollowUpFormMode.COMPLETE,
                    followUpError = "We could not determine your local time zone. Refresh the page and try again.",
                )
                return@post
            }

            val completion = try {
                networkManager.completeContactFollowUp(
                    context.tenantId,
                    context.user.id,
                    contactId,
                    followUpId,
                    CompleteContactFollowUpDto(timeZone = timeZone, interaction = null),
                )
            } catch (failure: Exception) {
                val error = failure.followUpFormError()
                if (error != null) {
                    call.respondContactOverview(
                        config = config,
                        networkManager = networkManager,
                        fileStorage = fileStorage,
                        context = context,
                        contactId = contactId,
                        selectedCompleteFollowUpId = followUpId,
                        followUpMode = FollowUpFormMode.COMPLETE,
                        followUpError = error,
                    )
                } else {
                    call.respondContactFailure(failure)
                }
                return@post
            }
            call.respondRedirect(followUpCompletionRedirect(contactId, completion, parameters["returnTo"]))
        }

        post("/contacts/{contactId}/follow-ups/{followUpId}/complete-with-interaction") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val followUpId = call.followUpIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val form = parameters.toContactInteractionForm()
            val timeZone = parameters.submittedTimeZone(context.timeZone)
            val validationError = form.validationError()
                ?: if (timeZone.toZoneIdOrNull() == null) {
                    "We could not determine your local time zone. Refresh the page and try again."
                } else {
                    null
                }
            if (validationError != null) {
                call.respondContactOverview(
                    config = config,
                    networkManager = networkManager,
                    fileStorage = fileStorage,
                    context = context,
                    contactId = contactId,
                    newInteraction = form,
                    selectedCompleteFollowUpId = followUpId,
                    followUpMode = FollowUpFormMode.COMPLETE_WITH_INTERACTION,
                    followUpError = validationError,
                )
                return@post
            }

            val completion = try {
                networkManager.completeContactFollowUp(
                    context.tenantId,
                    context.user.id,
                    contactId,
                    followUpId,
                    CompleteContactFollowUpDto(timeZone = timeZone, interaction = form.toCreateDto()),
                )
            } catch (failure: Exception) {
                val error = failure.followUpFormError()
                if (error != null) {
                    call.respondContactOverview(
                        config = config,
                        networkManager = networkManager,
                        fileStorage = fileStorage,
                        context = context,
                        contactId = contactId,
                        newInteraction = form,
                        selectedCompleteFollowUpId = followUpId,
                        followUpMode = FollowUpFormMode.COMPLETE_WITH_INTERACTION,
                        followUpError = error,
                    )
                } else {
                    call.respondContactFailure(failure)
                }
                return@post
            }
            call.respondRedirect(followUpCompletionRedirect(contactId, completion, null))
        }

        post("/contacts/{contactId}/follow-ups/{followUpId}/cancel") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val followUpId = call.followUpIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            try {
                networkManager.cancelContactFollowUp(context.tenantId, context.user.id, contactId, followUpId)
            } catch (failure: Exception) {
                val error = failure.followUpFormError()
                if (error != null) {
                    call.respondContactOverview(
                        config = config,
                        networkManager = networkManager,
                        fileStorage = fileStorage,
                        context = context,
                        contactId = contactId,
                        selectedCompleteFollowUpId = followUpId,
                        followUpMode = FollowUpFormMode.COMPLETE,
                        followUpError = error,
                    )
                } else {
                    call.respondContactFailure(failure)
                }
                return@post
            }
            call.respondRedirect("/contacts/$contactId?followUpUpdated=cancelled")
        }


        post("/contacts/{contactId}/interactions") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val form = parameters.toContactInteractionForm()
            val validationError = form.validationError()
            if (validationError != null) {
                call.respondContactOverview(config, networkManager, fileStorage, context, contactId, form, validationError)
                return@post
            }

            try {
                networkManager.registerContactInteraction(context.tenantId, context.user.id, contactId, form.toCreateDto())
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@post
            }
            call.respondRedirect("/contacts/$contactId")
        }

        post("/contacts/{contactId}/interactions/{interactionId}") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val interactionId = call.interactionIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val form = parameters.toContactInteractionForm()
            val validationError = form.validationError()
            if (validationError != null) {
                call.respondContactOverview(config, networkManager, fileStorage, context, contactId, form, validationError)
                return@post
            }

            try {
                networkManager.updateContactInteraction(context.tenantId, context.user.id, contactId, interactionId, form.toUpdateDto())
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@post
            }
            call.respondRedirect("/contacts/$contactId")
        }

        post("/contacts/{contactId}/interactions/{interactionId}/remove") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val interactionId = call.interactionIdOrNotFound() ?: return@post
            val parameters = call.receiveParameters()
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            try {
                networkManager.removeContactInteraction(context.tenantId, context.user.id, contactId, interactionId)
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@post
            }
            call.respondRedirect("/contacts/$contactId")
        }

        get("/contacts/{contactId}/edit") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@get
            val contactId = call.contactIdOrNotFound() ?: return@get
            val contact = try {
                networkManager.getContact(context.tenantId, context.user.id, contactId)
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@get
            } ?: run {
                call.contactNotFound()
                return@get
            }
            call.respondContactForm(
                config,
                contact.toContactForm(contact.image?.fileKey?.let { fileStorage.createImageUrl(config.fileStorageBucket, it) }),
                "/contacts/$contactId",
                "Edit contact",
                "Save changes",
                imageUploadError = call.request.queryParameters["imageUpload"] == "failed",
            )
        }

        post("/contacts/{contactId}") {
            val context = call.personalTenantContext(sessionValidator, membershipManager) ?: return@post
            val contactId = call.contactIdOrNotFound() ?: return@post
            val submission = call.receiveContactSubmission()
            val parameters = submission.parameters
            if (!call.hasValidCsrfToken(parameters["csrfToken"])) {
                call.respondText("Your form expired. Refresh the page and try again.", status = HttpStatusCode.Forbidden)
                return@post
            }

            val form = parameters.toContactForm()
            val validationError = form.validationError() ?: submission.imageError
            if (validationError != null) {
                call.respondContactForm(
                    config,
                    form,
                    "/contacts/$contactId",
                    "Edit contact",
                    "Save changes",
                    validationError,
                )
                return@post
            }

            try {
                networkManager.updateContact(context.tenantId, context.user.id, contactId, form.toUpdateDto())
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@post
            }
            try {
                when {
                    submission.image != null ->
                        uploadContactImage(fileStorage, config.fileStorageBucket, networkManager, context, contactId, submission.image)

                    submission.removeImage ->
                        removeContactImage(fileStorage, config.fileStorageBucket, networkManager, context, contactId)
                }
            } catch (failure: Exception) {
                fileStorageLogger.error("Could not update contact image for contact $contactId", failure)
                call.respondRedirect("/contacts/$contactId/edit?imageUpload=failed")
                return@post
            }
            call.respondRedirect("/dashboard")
        }
    }
}

private fun authenticationModel(config: ApplicationConfig, mode: String, title: String): Map<String, Any?> =
    mapOf(
        "mode" to mode,
        "title" to title,
        "hankoApiUrl" to config.hankoApiUrl,
        "hankoCookieDomain" to config.hankoCookieDomain,
    )

private suspend fun ApplicationCall.authenticatedUser(validator: SessionValidator): AuthenticatedUser? =
    when (val result = validator.validate(request.cookies["hanko"])) {
        is HankoSessionResult.Authenticated -> result.user
        HankoSessionResult.Missing,
        HankoSessionResult.Invalid,
        -> {
            respondRedirect("/sign-in")
            null
        }

        HankoSessionResult.Unavailable -> {
            serviceUnavailable()
            null
        }
    }

private suspend fun ApplicationCall.loadProfile(
    membershipManager: MembershipManager,
    user: AuthenticatedUser,
): ProfileLookup =
    try {
        membershipManager.getProfile(GetProfileRequest(user.id)).let { response ->
            val personalTenant = response.tenants.singleOrNull { it.type == TenantTypeDto.PERSONAL }
                ?: return ProfileLookup.Unavailable
            ProfileLookup.Found(response.profile, personalTenant.id)
        }
    } catch (exception: IfxRemoteException) {
        if (exception.remoteType == ProfileNotFoundException::class.qualifiedName) {
            ProfileLookup.Missing
        } else {
            ProfileLookup.Unavailable
        }
    } catch (_: Exception) {
        ProfileLookup.Unavailable
    }

private sealed interface ProfileLookup {
    data class Found(val profile: ProfileDto, val personalTenantId: kotlin.uuid.Uuid) : ProfileLookup

    data object Missing : ProfileLookup

    data object Unavailable : ProfileLookup
}

private data class PersonalTenantContext(
    val user: AuthenticatedUser,
    val profile: ProfileDto,
    val tenantId: kotlin.uuid.Uuid,
    val timeZone: ZoneId,
)

private suspend fun ApplicationCall.personalTenantContext(
    validator: SessionValidator,
    membershipManager: MembershipManager,
): PersonalTenantContext? {
    val user = authenticatedUser(validator) ?: return null
    return when (val lookup = loadProfile(membershipManager, user)) {
        is ProfileLookup.Found -> PersonalTenantContext(
            user = user,
            profile = lookup.profile,
            tenantId = lookup.personalTenantId,
            timeZone = browserTimeZone(),
        )
        ProfileLookup.Missing -> {
            respondRedirect("/onboarding")
            null
        }

        ProfileLookup.Unavailable -> {
            serviceUnavailable()
            null
        }
    }
}

private fun ApplicationCall.browserTimeZone(): ZoneId =
    request.cookies[TIME_ZONE_COOKIE]?.toZoneIdOrNull() ?: ZoneOffset.UTC

private suspend fun ApplicationCall.respondContactForm(
    config: ApplicationConfig,
    contact: ContactForm,
    formAction: String,
    title: String,
    submitLabel: String,
    error: String? = null,
    imageUploadError: Boolean = false,
) {
    respondPage(
        "contact-form.ftl",
        mapOf(
            "contact" to contact,
            "formAction" to formAction,
            "title" to title,
            "submitLabel" to submitLabel,
            "error" to (error ?: imageUploadErrorMessage(imageUploadError)),
            "csrfToken" to csrfToken(config.secureCookies),
            "hankoApiUrl" to config.hankoApiUrl,
            "hankoCookieDomain" to config.hankoCookieDomain,
        ),
    )
}

private suspend fun ApplicationCall.respondContactOverview(
    config: ApplicationConfig,
    networkManager: NetworkManager,
    fileStorage: FileStorage,
    context: PersonalTenantContext,
    contactId: kotlin.uuid.Uuid,
    newInteraction: ContactInteractionForm = ContactInteractionForm(),
    error: String? = null,
    newFollowUp: ContactFollowUpForm? = null,
    followUpError: String? = null,
    followUpMode: String? = null,
    selectedCompleteFollowUpId: kotlin.uuid.Uuid? = null,
    selectedRescheduleFollowUpId: kotlin.uuid.Uuid? = null,
    selectedFrequencyFollowUpId: kotlin.uuid.Uuid? = null,
    rescheduleDueOn: String? = null,
    frequencyForm: ContactFollowUpFrequencyForm? = null,
    followUpMessage: String? = null,
) {
    val overview = try {
        networkManager.getContactOverview(context.tenantId, context.user.id, contactId)
    } catch (failure: Exception) {
        respondContactFailure(failure)
        return
    } ?: run {
        contactNotFound()
        return
    }
    val openFollowUps = overview.followUps
        .filter { it.status == ContactFollowUpStatusDto.OPEN }
        .map(ContactFollowUpOverview::from)
        .sortedWith(compareBy<ContactFollowUpOverview> { it.dueOn }.thenBy { it.followUpId })
    val followUpGroups = listOf(
        ContactFollowUpGroup(
            label = "Recurring follow-up",
            recurring = true,
            items = openFollowUps.filter { it.isRecurring },
        ),
        ContactFollowUpGroup(
            label = "One-time follow-ups",
            recurring = false,
            items = openFollowUps.filterNot { it.isRecurring },
        ),
    )
    val selectedReschedule = selectedRescheduleFollowUpId?.toString()
        ?.let { id -> openFollowUps.firstOrNull { it.followUpId == id } }
    val selectedFrequency = overview.followUps.firstOrNull {
        it.followUpId == selectedFrequencyFollowUpId && it.status == ContactFollowUpStatusDto.OPEN
    }
    val resolvedMode = followUpMode ?: when {
        selectedCompleteFollowUpId != null -> FollowUpFormMode.COMPLETE
        selectedRescheduleFollowUpId != null -> FollowUpFormMode.RESCHEDULE
        selectedFrequencyFollowUpId != null -> FollowUpFormMode.FREQUENCY
        else -> null
    }
    respondPage(
        "contact-overview.ftl",
        mapOf(
            "contact" to overview.toContactOverviewContact(fileStorage, config.fileStorageBucket),
            "interactions" to overview.interactions.map(ContactInteractionOverview::from),
            "followUpGroups" to followUpGroups,
            "newInteraction" to newInteraction,
            "channels" to interactionChannelOptions(),
            "error" to error,
            "newFollowUp" to (newFollowUp ?: ContactFollowUpForm.forToday(LocalDate.now(context.timeZone))),
            "followUpFrequencies" to contactFollowUpFrequencyOptions(),
            "followUpError" to followUpError,
            "followUpMode" to resolvedMode,
            "selectedCompleteFollowUpId" to selectedCompleteFollowUpId?.toString(),
            "selectedRescheduleFollowUpId" to selectedRescheduleFollowUpId?.toString(),
            "selectedFrequencyFollowUpId" to selectedFrequencyFollowUpId?.toString(),
            "rescheduleDueOn" to (rescheduleDueOn ?: selectedReschedule?.dueOn),
            "frequencyForm" to (frequencyForm ?: selectedFrequency?.let(ContactFollowUpFrequencyForm::from)),
            "followUpMessage" to followUpMessage,
            "timeZone" to context.timeZone.id,
            "csrfToken" to csrfToken(config.secureCookies),
            "hankoApiUrl" to config.hankoApiUrl,
            "hankoCookieDomain" to config.hankoCookieDomain,
        ),
    )
}

private fun imageUploadErrorMessage(imageUploadError: Boolean): String? =
    if (imageUploadError) "Your contact was saved, but its photo could not be uploaded. Please try again." else null

internal data class ContactListItem(
    val contactId: kotlin.uuid.Uuid,
    val name: String,
    val primaryEmailAddress: String?,
    val imageUrl: String?,
)

private fun TenantContactListItemDto.toContactListItem(fileStorage: FileStorage, bucket: String): ContactListItem = ContactListItem(
    contactId = contactId,
    name = name,
    primaryEmailAddress = primaryEmailAddress,
    imageUrl = image?.fileKey?.let { fileStorage.createImageUrl(bucket, it) },
)

internal data class ContactOverviewContact(
    val contactId: kotlin.uuid.Uuid,
    val name: String,
    val emails: List<EmailAddressDto>,
    val phoneNumbers: List<PhoneNumberDto>,
    val workTitle: String?,
    val workOrganization: String?,
    val note: String?,
    val imageUrl: String?,
)

internal data class ContactInteractionOverview(
    val interactionId: kotlin.uuid.Uuid,
    val channel: String,
    val channelLabel: String,
    val notes: String?,
    val occurredAt: String,
    val occurredAtDisplay: String,
    val occurredAtInput: String,
) {
    companion object {
        fun from(interaction: ContactInteractionDto): ContactInteractionOverview {
            val form = interaction.toContactInteractionForm()
            return ContactInteractionOverview(
                interactionId = interaction.interactionId,
                channel = interaction.channel.name,
                channelLabel = interaction.channel.displayName(),
                notes = interaction.notes,
                occurredAt = interaction.occurredAt,
                occurredAtDisplay = interaction.occurredAt.toInteractionTimestamp(),
                occurredAtInput = form.occurredAtInput,
            )
        }
    }
}

private object FollowUpFormMode {
    const val CREATE = "CREATE"
    const val RESCHEDULE = "RESCHEDULE"
    const val FREQUENCY = "FREQUENCY"
    const val COMPLETE = "COMPLETE"
    const val COMPLETE_WITH_INTERACTION = "COMPLETE_WITH_INTERACTION"
}

internal data class DashboardFollowUp(
    val followUpId: String,
    val contactId: String,
    val contactName: String,
    val imageUrl: String?,
    val dueOn: String,
    val dueLabel: String,
    val typeLabel: String,
    val frequencyLabel: String?,
)

internal data class DashboardFollowUpSection(
    val label: String,
    val items: List<DashboardFollowUp>,
)

internal data class ContactFollowUpGroup(
    val label: String,
    val recurring: Boolean,
    val items: List<ContactFollowUpOverview>,
) {
    fun isRecurring(): Boolean = recurring
}

internal data class ContactFollowUpOverview(
    val followUpId: String,
    val dueOn: String,
    val dueLabel: String,
    val recurrenceLabel: String,
    val statusLabel: String,
    val isRecurring: Boolean,
) {
    companion object {
        fun from(followUp: ContactFollowUpDto): ContactFollowUpOverview {
            val recurrence = followUp.recurrence
            return ContactFollowUpOverview(
                followUpId = followUp.followUpId.toString(),
                dueOn = followUp.dueOn,
                dueLabel = followUp.dueOn.toLocalDateOrNull()?.format(FOLLOW_UP_DATE_FORMATTER) ?: followUp.dueOn,
                recurrenceLabel = recurrence?.displayName() ?: "One-time",
                statusLabel = followUp.status.displayName(),
                isRecurring = recurrence != null,
            )
        }
    }
}

private fun List<netvaerke.manager.network.DueContactFollowUpDto>.toDashboardFollowUpSections(
    fileStorage: FileStorage,
    bucket: String,
    today: LocalDate,
): List<DashboardFollowUpSection> {
    val grouped = mapNotNull { due ->
        val dueDate = due.followUp.dueOn.toLocalDateOrNull() ?: return@mapNotNull null
        DashboardFollowUp(
            followUpId = due.followUp.followUpId.toString(),
            contactId = due.contact.contactId.toString(),
            contactName = due.contact.name,
            imageUrl = due.contact.image?.fileKey?.let { fileStorage.createImageUrl(bucket, it) },
            dueOn = due.followUp.dueOn,
            dueLabel = dueDate.format(FOLLOW_UP_DATE_FORMATTER),
            typeLabel = if (due.followUp.recurrence == null) "One-time" else "Recurring",
            frequencyLabel = due.followUp.recurrence?.displayName(),
        ) to dueDate
    }.groupBy { (_, dueDate) ->
        when {
            dueDate < today -> "OVERDUE"
            dueDate == today -> "TODAY"
            else -> "UPCOMING"
        }
    }
    return listOf(
        "OVERDUE" to "Overdue",
        "TODAY" to "Today",
        "UPCOMING" to "Next 7 days",
    ).mapNotNull { (key, label) ->
        grouped[key]
            ?.sortedWith(compareBy<Pair<DashboardFollowUp, LocalDate>> { it.second }.thenBy { it.first.contactName })
            ?.map { it.first }
            ?.takeIf { it.isNotEmpty() }
            ?.let { DashboardFollowUpSection(label, it) }
    }
}

private fun ContactFollowUpCadenceDto.displayName(): String {
    frequency?.let { return it.displayName() }
    val singular = unit.name.lowercase().removeSuffix("s")
    return "Every $amount ${if (amount == 1) singular else "${singular}s"}"
}

private fun ContactFollowUpStatusDto.displayName(): String = when (this) {
    ContactFollowUpStatusDto.OPEN -> "Open"
    ContactFollowUpStatusDto.DONE -> "Completed"
    ContactFollowUpStatusDto.CANCELLED -> "Cancelled"
}

private fun ContactOverviewDto.toContactOverviewContact(fileStorage: FileStorage, bucket: String): ContactOverviewContact =
    ContactOverviewContact(
        contactId = contact.contactId,
        name = contact.name,
        emails = contact.emails,
        phoneNumbers = contact.phoneNumbers,
        workTitle = contact.workInfo?.title,
        workOrganization = contact.workInfo?.organization,
        note = contact.note?.value,
        imageUrl = contact.image?.fileKey?.let { fileStorage.createImageUrl(bucket, it) },
    )

private fun String.toInteractionTimestamp(): String = runCatching {
    Instant.parse(this).atOffset(ZoneOffset.UTC).format(INTERACTION_TIMESTAMP_FORMATTER)
}.getOrDefault(this)

private fun FileStorage.createImageUrl(bucket: String, fileKey: String): String? =
    runCatching { createGetUrl(bucket, fileKey, 1.hours) }.getOrNull()

private suspend fun uploadContactImage(
    fileStorage: FileStorage,
    bucket: String,
    networkManager: NetworkManager,
    context: PersonalTenantContext,
    contactId: kotlin.uuid.Uuid,
    image: ContactImageUpload,
) {
    val fileKey = networkManager.reserveContactImageUpload(context.tenantId, context.user.id, contactId).fileKey
    try {
        fileStorage.putFile(bucket, fileKey, image.contentType, image.content.size.toLong(), image.content.inputStream())
    } catch (failure: Exception) {
        fileStorage.deleteImageIgnoringFailure(bucket, fileKey)
        throw failure
    }

    try {
        val update = networkManager.setContactImage(context.tenantId, context.user.id, contactId, fileKey)
        update.previousFileKey?.let { fileStorage.deleteImageIgnoringFailure(bucket, it) }
    } catch (failure: Exception) {
        fileStorage.deleteImageIgnoringFailure(bucket, fileKey)
        throw failure
    }
}

private suspend fun removeContactImage(
    fileStorage: FileStorage,
    bucket: String,
    networkManager: NetworkManager,
    context: PersonalTenantContext,
    contactId: kotlin.uuid.Uuid,
) {
    val update = networkManager.setContactImage(context.tenantId, context.user.id, contactId, null)
    update.previousFileKey?.let { fileStorage.deleteImageIgnoringFailure(bucket, it) }
}

private suspend fun FileStorage.deleteImageIgnoringFailure(bucket: String, fileKey: String) {
    try {
        deleteFile(bucket, fileKey)
    } catch (failure: Exception) {
        fileStorageLogger.error("Could not delete contact image object $fileKey from $bucket", failure)
    }
}

private val fileStorageLogger = LoggerFactory.getLogger("netvaerke.application.web.FileStorage")

private suspend fun ApplicationCall.contactIdOrNotFound(): kotlin.uuid.Uuid? =
    runCatching { kotlin.uuid.Uuid.parse(parameters["contactId"].orEmpty()) }.getOrElse {
        contactNotFound()
        return null
    }

private suspend fun ApplicationCall.interactionIdOrNotFound(): kotlin.uuid.Uuid? =
    runCatching { kotlin.uuid.Uuid.parse(parameters["interactionId"].orEmpty()) }.getOrElse {
        interactionNotFound()
        return null
    }
private suspend fun ApplicationCall.followUpIdOrNotFound(): kotlin.uuid.Uuid? =
    runCatching { kotlin.uuid.Uuid.parse(parameters["followUpId"].orEmpty()) }.getOrElse {
        followUpNotFound()
        return null
    }

private fun String?.parseFollowUpId(): kotlin.uuid.Uuid? =
    this?.let { runCatching { kotlin.uuid.Uuid.parse(it) }.getOrNull() }

private fun io.ktor.http.Parameters.submittedTimeZone(fallback: ZoneId): String =
    this["timeZone"]?.trim().takeUnless { it.isNullOrBlank() } ?: fallback.id

private fun String.toZoneIdOrNull(): ZoneId? = runCatching { ZoneId.of(this) }.getOrNull()

private fun ApplicationCall.followUpMessage(): String? {
    val query = request.queryParameters
    return when {
        query["followUpCreated"] == "true" -> "Follow-up scheduled."
        query["followUpUpdated"] == "rescheduled" -> "Follow-up rescheduled."
        query["followUpUpdated"] == "frequency" -> "Follow-up frequency updated."
        query["followUpUpdated"] == "cancelled" -> "Follow-up cancelled."
        query["followUpCompleted"] != null -> {
            val next = query["followUpCompleted"].orEmpty().toLocalDateOrNull()
            if (next == null) {
                "Follow-up completed."
            } else {
                "Follow-up completed. Next due ${next.format(FOLLOW_UP_DATE_FORMATTER)}."
            }
        }
        else -> null
    }
}

private fun followUpCompletionRedirect(
    contactId: kotlin.uuid.Uuid,
    completion: netvaerke.manager.network.ContactFollowUpCompletionDto,
    returnTo: String?,
): String {
    val destination = if (returnTo == "dashboard") "/dashboard" else "/contacts/$contactId"
    return "$destination?followUpCompleted=${completion.next?.dueOn.orEmpty()}"
}

private fun Exception.followUpFormError(): String? {
    val remoteType = (this as? IfxRemoteException)?.remoteType
    return when {
        this is ActiveContactFollowUpRecurrenceException ||
            remoteType == ActiveContactFollowUpRecurrenceException::class.qualifiedName ->
            "This contact already has a repeating follow-up."
        this is ContactFollowUpNotOpenException ||
            remoteType == ContactFollowUpNotOpenException::class.qualifiedName ->
            "This follow-up is no longer open. Refresh the page and try again."
        this is ContactFollowUpNotRecurringException ||
            remoteType == ContactFollowUpNotRecurringException::class.qualifiedName ->
            "This follow-up is not repeating."
        this is ContactFollowUpAlreadyCompletedException ||
            remoteType == ContactFollowUpAlreadyCompletedException::class.qualifiedName ->
            "This follow-up was already completed. Refresh the page and try again."
        this is ContactFollowUpAlreadyCancelledException ||
            remoteType == ContactFollowUpAlreadyCancelledException::class.qualifiedName ->
            "This follow-up was already cancelled. Refresh the page and try again."
        this is ContactFollowUpCancelledException ||
            remoteType == ContactFollowUpCancelledException::class.qualifiedName ->
            "This follow-up was cancelled. Refresh the page and try again."
        remoteType == "netvaerke.manager.network.InvalidContactFollowUpTimeZoneException" ->
            "We could not determine your local time zone. Refresh the page and try again."
        else -> null
    }
}

private fun Exception.isFollowUpNotFound(): Boolean =
    this is ContactFollowUpNotFoundException ||
        (this as? IfxRemoteException)?.remoteType == ContactFollowUpNotFoundException::class.qualifiedName


private suspend fun ApplicationCall.respondContactFailure(failure: Exception) {
    when {
        failure is AuthorizationDeniedException -> respondText(
            "You do not have permission to manage these contacts.",
            status = HttpStatusCode.Forbidden,
        )

        failure is ContactNotFoundException -> contactNotFound()
        failure is ContactInteractionNotFoundException -> interactionNotFound()
        failure.isFollowUpNotFound() -> followUpNotFound()
        else -> when ((failure as? IfxRemoteException)?.remoteType) {
            AuthorizationDeniedException::class.qualifiedName -> respondText(
                "You do not have permission to manage these contacts.",
                status = HttpStatusCode.Forbidden,
            )

            ContactNotFoundException::class.qualifiedName -> contactNotFound()
            ContactInteractionNotFoundException::class.qualifiedName -> interactionNotFound()
            ContactFollowUpNotFoundException::class.qualifiedName -> followUpNotFound()
            else -> serviceUnavailable()
        }
    }
}

private suspend fun ApplicationCall.contactNotFound() {
    respondText("Contact not found.", status = HttpStatusCode.NotFound)
}

private suspend fun ApplicationCall.followUpNotFound() {
    respondText("Follow-up not found.", status = HttpStatusCode.NotFound)
}
private suspend fun ApplicationCall.interactionNotFound() {
    respondText("Interaction not found.", status = HttpStatusCode.NotFound)
}


private suspend fun ApplicationCall.respondOnboarding(
    config: ApplicationConfig,
    user: AuthenticatedUser,
    name: String = "",
    email: String = user.email.orEmpty(),
    error: String? = null,
) {
    respondPage(
        "onboarding.ftl",
        mapOf(
            "name" to name,
            "email" to email,
            "error" to error,
            "csrfToken" to csrfToken(config.secureCookies),
        ),
    )
}

private suspend fun ApplicationCall.respondPage(template: String, model: Map<String, Any?> = emptyMap()) {
    respond(FreeMarkerContent(template, model))
}

private suspend fun ApplicationCall.serviceUnavailable() {
    respondText(
        "netværke is temporarily unable to verify your account. Please try again shortly.",
        status = HttpStatusCode.ServiceUnavailable,
    )
}

private fun ApplicationCall.csrfToken(secure: Boolean): String {
    val existing = request.cookies[CSRF_COOKIE_NAME]
    if (existing != null) return existing

    val token = ByteArray(32).also(SecureRandom()::nextBytes)
        .let(Base64.getUrlEncoder().withoutPadding()::encodeToString)
    response.cookies.append(
        Cookie(
            name = CSRF_COOKIE_NAME,
            value = token,
            path = "/",
            secure = secure,
            httpOnly = false,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
    return token
}

private fun ApplicationCall.hasValidCsrfToken(actual: String?): Boolean {
    val expected = request.cookies[CSRF_COOKIE_NAME] ?: return false
    return actual != null && MessageDigest.isEqual(expected.encodeToByteArray(), actual.encodeToByteArray())
}

private fun validateProfileDetails(name: String, email: String): String? =
    when {
        name.isBlank() -> "Enter your name."
        name.length > 255 -> "Your name must be 255 characters or fewer."
        email.isBlank() -> "Enter your email address."
        email.length > 255 || !EMAIL_PATTERN.matches(email) -> "Enter a valid email address."
        else -> null
    }

private const val CSRF_COOKIE_NAME = "netvaerke_csrf"

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

private val INTERACTION_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm 'UTC'")
private const val TIME_ZONE_COOKIE = "netvaerke_timezone"
private const val DASHBOARD_FOLLOW_UP_DAYS = 7L

private val FOLLOW_UP_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu")
