package netvaerke.application.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ApplicationConfigTest {
    private val requiredEnvironment = mapOf(
        "NATS_URL" to "nats://localhost:4222",
        "HANKO_API_URL" to "https://auth.netvaerke.com/",
    )

    @Test
    fun `loads defaults for the local web application`() {
        val config = ApplicationConfig.fromEnvironment(requiredEnvironment)

        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals("netvaerke.membership-manager.v1", config.membershipSubject)
        assertEquals(5.seconds, config.natsRequestTimeout)
        assertEquals("https://auth.netvaerke.com", config.hankoApiUrl)
        assertEquals("https://auth.netvaerke.com", config.hankoValidationApiUrl)
        assertEquals(null, config.hankoCookieDomain)
        assertEquals(true, config.secureCookies)
    }

    @Test
    fun `loads separate browser and internal Hanko URLs`() {
        val config = ApplicationConfig.fromEnvironment(
            requiredEnvironment + mapOf(
                "HANKO_API_URL" to "https://auth.netvaerke.com",
                "HANKO_VALIDATION_API_URL" to "http://hanko:8000/",
                "HANKO_COOKIE_DOMAIN" to ".netvaerke.com",
                "SECURE_COOKIES" to "false",
                "PORT" to "9090",
            ),
        )

        assertEquals(9090, config.port)
        assertEquals("http://hanko:8000", config.hankoValidationApiUrl)
        assertEquals(".netvaerke.com", config.hankoCookieDomain)
        assertEquals(false, config.secureCookies)
    }

    @Test
    fun `rejects a missing Hanko API URL`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ApplicationConfig.fromEnvironment(requiredEnvironment - "HANKO_API_URL")
        }

        assertEquals(
            "HANKO_API_URL must be configured. For local development, pass --config ui/web/config/local.properties",
            failure.message,
        )
    }
}
