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
import netvaerke.access.profile.Profile
import netvaerke.ifx.IfxRemoteException
import netvaerke.manager.membership.GetProfileRequest
import netvaerke.manager.membership.MembershipManager
import netvaerke.manager.membership.ProfileNotFoundException
import netvaerke.manager.membership.RegisterProfileRequest

internal fun Application.configureWebApplication(
    config: ApplicationConfig,
    membershipManager: MembershipManager,
) {
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(Application::class.java.classLoader, "templates")
    }

    val hankoSessionValidator = HankoSessionValidator(config.hankoValidationApiUrl)

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
            val user = call.authenticatedUser(hankoSessionValidator) ?: return@get
            when (val profile = call.loadProfile(membershipManager, user)) {
                is ProfileLookup.Found -> call.respondPage(
                    "dashboard.ftl",
                    mapOf(
                        "profile" to profile.profile,
                        "hankoApiUrl" to config.hankoApiUrl,
                        "hankoCookieDomain" to config.hankoCookieDomain,
                    ),
                )

                ProfileLookup.Missing -> call.respondRedirect("/onboarding")
                ProfileLookup.Unavailable -> call.serviceUnavailable()
            }
        }

        get("/onboarding") {
            val user = call.authenticatedUser(hankoSessionValidator) ?: return@get
            when (call.loadProfile(membershipManager, user)) {
                is ProfileLookup.Found -> call.respondRedirect("/dashboard")
                ProfileLookup.Missing -> call.respondOnboarding(config, user)
                ProfileLookup.Unavailable -> call.serviceUnavailable()
            }
        }

        post("/onboarding") {
            val user = call.authenticatedUser(hankoSessionValidator) ?: return@post
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
    }
}

private fun authenticationModel(config: ApplicationConfig, mode: String, title: String): Map<String, Any?> =
    mapOf(
        "mode" to mode,
        "title" to title,
        "hankoApiUrl" to config.hankoApiUrl,
        "hankoCookieDomain" to config.hankoCookieDomain,
    )

private suspend fun ApplicationCall.authenticatedUser(validator: HankoSessionValidator): AuthenticatedUser? =
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
        ProfileLookup.Found(membershipManager.getProfile(GetProfileRequest(user.id)).profile)
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
    data class Found(val profile: Profile) : ProfileLookup

    data object Missing : ProfileLookup

    data object Unavailable : ProfileLookup
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
