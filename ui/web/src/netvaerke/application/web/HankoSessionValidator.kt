package netvaerke.application.web

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class HankoSessionValidator(
    private val validationApiUrl: String,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun validate(sessionToken: String?): HankoSessionResult {
        if (sessionToken.isNullOrBlank()) return HankoSessionResult.Missing

        val request = HttpRequest.newBuilder(URI.create("$validationApiUrl/sessions/validate"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(HankoSessionValidationRequest(sessionToken))))
            .build()

        val response = try {
            withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }
        } catch (_: Exception) {
            return HankoSessionResult.Unavailable
        }

        if (response.statusCode() in 400..499) return HankoSessionResult.Invalid
        if (response.statusCode() !in 200..299) return HankoSessionResult.Unavailable

        val validation = try {
            json.decodeFromString<HankoSessionValidationResponse>(response.body())
        } catch (_: Exception) {
            return HankoSessionResult.Unavailable
        }
        if (!validation.isValid) return HankoSessionResult.Invalid

        val claims = validation.claims ?: return HankoSessionResult.Invalid
        val userId = try {
            Uuid.parse(claims.subject)
        } catch (_: IllegalArgumentException) {
            return HankoSessionResult.Invalid
        }

        return HankoSessionResult.Authenticated(
            user = AuthenticatedUser(
                id = userId,
                email = claims.email?.address,
            ),
        )
    }
}

internal sealed interface HankoSessionResult {
    data object Missing : HankoSessionResult

    data object Invalid : HankoSessionResult

    data object Unavailable : HankoSessionResult

    data class Authenticated(val user: AuthenticatedUser) : HankoSessionResult
}

internal data class AuthenticatedUser(
    val id: Uuid,
    val email: String?,
)

@Serializable
private data class HankoSessionValidationRequest(
    @SerialName("session_token") val sessionToken: String,
)

@Serializable
private data class HankoSessionValidationResponse(
    @SerialName("is_valid") val isValid: Boolean,
    val claims: HankoClaims? = null,
)

@Serializable
private data class HankoClaims(
    val subject: String,
    val email: HankoEmail? = null,
)

@Serializable
private data class HankoEmail(
    val address: String,
)
