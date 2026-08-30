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
import java.util.Base64
import netvaerke.businesslogic.network.AuthorizationDeniedException
import netvaerke.businesslogic.network.ContactNotFoundException
import netvaerke.businesslogic.network.NetworkManager
import netvaerke.businesslogic.network.TenantContactListItemDto
import netvaerke.ifx.IfxRemoteException
import netvaerke.manager.membership.GetProfileRequest
import netvaerke.manager.membership.MembershipManager
import netvaerke.manager.membership.ProfileDto
import netvaerke.manager.membership.ProfileNotFoundException
import netvaerke.manager.membership.RegisterProfileRequest
import netvaerke.manager.membership.TenantTypeDto
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

internal fun Application.configureWebApplication(
    config: ApplicationConfig,
    membershipManager: MembershipManager,
    networkManager: NetworkManager,
    fileStorage: FileStorage,
    sessionValidator: SessionValidator = HankoSessionValidator(config.hankoValidationApiUrl),
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
            val contacts = try {
                networkManager.getTenantContacts(context.tenantId, context.user.id)
            } catch (failure: Exception) {
                call.respondContactFailure(failure)
                return@get
            }
            call.respondPage(
                "dashboard.ftl",
                mapOf(
                    "profile" to context.profile,
                    "contacts" to contacts.map { it.toContactListItem(fileStorage, config.fileStorageBucket) },
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
)

private suspend fun ApplicationCall.personalTenantContext(
    validator: SessionValidator,
    membershipManager: MembershipManager,
): PersonalTenantContext? {
    val user = authenticatedUser(validator) ?: return null
    return when (val lookup = loadProfile(membershipManager, user)) {
        is ProfileLookup.Found -> PersonalTenantContext(user, lookup.profile, lookup.personalTenantId)
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

private suspend fun ApplicationCall.respondContactFailure(failure: Exception) {
    when ((failure as? IfxRemoteException)?.remoteType) {
        AuthorizationDeniedException::class.qualifiedName -> respondText(
            "You do not have permission to manage these contacts.",
            status = HttpStatusCode.Forbidden,
        )

        ContactNotFoundException::class.qualifiedName -> contactNotFound()
        else -> serviceUnavailable()
    }
}

private suspend fun ApplicationCall.contactNotFound() {
    respondText("Contact not found.", status = HttpStatusCode.NotFound)
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
