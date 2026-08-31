package netvaerke.application.membership

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ApplicationConfigTest {
    private val requiredEnvironment = mapOf(
        "DATABASE_URL" to "jdbc:postgresql://localhost/netvaerke",
        "DATABASE_USER" to "netvaerke",
        "DATABASE_PASSWORD" to "secret",
        "NATS_URL" to "nats://localhost:4222",
    )

    @Test
    fun `loads required values and defaults`() {
        val config = ApplicationConfig.fromEnvironment(requiredEnvironment)

        assertEquals("netvaerke.membership-manager.v1", config.membershipSubject)
        assertEquals(config.membershipSubject, config.membershipQueueGroup)
        assertEquals(5.seconds, config.natsRequestTimeout)
    }

    @Test
    fun `loads NATS overrides`() {
        val config = ApplicationConfig.fromEnvironment(
            requiredEnvironment + mapOf(
                "MEMBERSHIP_NATS_SUBJECT" to "membership.test",
                "MEMBERSHIP_NATS_QUEUE_GROUP" to "membership-workers",
                "MEMBERSHIP_NATS_TIMEOUT_SECONDS" to "12",
            ),
        )

        assertEquals("membership.test", config.membershipSubject)
        assertEquals("membership-workers", config.membershipQueueGroup)
        assertEquals(12.seconds, config.natsRequestTimeout)
    }

    @Test
    fun `loads a properties file with environment overrides`() {
        val configFile = Files.createTempFile("membership-manager", ".properties")
        try {
            Files.writeString(
                configFile,
                """
                    DATABASE_URL=jdbc:postgresql://localhost/from-file
                    DATABASE_USER=file-user
                    DATABASE_PASSWORD=file-password
                    NATS_URL=nats://from-file:4222
                    MEMBERSHIP_NATS_TIMEOUT_SECONDS=9
                """.trimIndent(),
            )

            val config = ApplicationConfig.load(
                arguments = arrayOf("--config", configFile.toString()),
                environment = mapOf(
                    "DATABASE_USER" to "environment-user",
                    "NATS_URL" to "nats://environment:4222",
                ),
            )

            assertEquals("jdbc:postgresql://localhost/from-file", config.databaseUrl)
            assertEquals("environment-user", config.databaseUser)
            assertEquals("file-password", config.databasePassword)
            assertEquals("nats://environment:4222", config.natsUrl)
            assertEquals(9.seconds, config.natsRequestTimeout)
        } finally {
            Files.deleteIfExists(configFile)
        }
    }

    @Test
    fun `rejects missing required values`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ApplicationConfig.fromEnvironment(requiredEnvironment - "NATS_URL")
        }

        assertEquals("NATS_URL must be configured", failure.message)
    }

    @Test
    fun `rejects invalid timeout`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ApplicationConfig.fromEnvironment(
                requiredEnvironment + ("MEMBERSHIP_NATS_TIMEOUT_SECONDS" to "soon"),
            )
        }

        assertEquals("MEMBERSHIP_NATS_TIMEOUT_SECONDS must be a positive integer", failure.message)
    }

    @Test
    fun `rejects invalid application arguments`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ApplicationConfig.load(arrayOf("--config"), requiredEnvironment)
        }

        assertEquals("Application arguments must be empty or '--config <path>'", failure.message)
    }

    @Test
    fun `rejects a missing configuration file`() {
        val path = "does-not-exist-${System.nanoTime()}.properties"
        val failure = assertFailsWith<IllegalArgumentException> {
            ApplicationConfig.load(arrayOf("--config", path), requiredEnvironment)
        }

        assertEquals("Configuration file does not exist: $path", failure.message)
    }
}
