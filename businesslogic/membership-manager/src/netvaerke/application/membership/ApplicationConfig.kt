package netvaerke.application.membership

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class ApplicationConfig(
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val natsUrl: String,
    val membershipSubject: String,
    val membershipQueueGroup: String,
    val natsRequestTimeout: Duration,
) {
    companion object {
        private const val DEFAULT_MEMBERSHIP_SUBJECT = "netvaerke.membership-manager.v1"

        fun load(
            arguments: Array<String>,
            environment: Map<String, String> = System.getenv(),
        ): ApplicationConfig {
            val fileValues = arguments.configFile()?.let(::readProperties).orEmpty()
            return fromEnvironment(fileValues + environment)
        }

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ApplicationConfig {
            val subject = environment.nonBlankOrDefault("MEMBERSHIP_NATS_SUBJECT", DEFAULT_MEMBERSHIP_SUBJECT)
            val timeoutSeconds = environment["MEMBERSHIP_NATS_TIMEOUT_SECONDS"]?.let { configured ->
                requireNotNull(configured.toLongOrNull()) {
                    "MEMBERSHIP_NATS_TIMEOUT_SECONDS must be a positive integer"
                }
            } ?: 5L
            require(timeoutSeconds > 0) { "MEMBERSHIP_NATS_TIMEOUT_SECONDS must be a positive integer" }

            return ApplicationConfig(
                databaseUrl = environment.requireNonBlank("DATABASE_URL"),
                databaseUser = environment.requireNonBlank("DATABASE_USER"),
                databasePassword = environment.requireNonBlank("DATABASE_PASSWORD"),
                natsUrl = environment.requireNonBlank("NATS_URL"),
                membershipSubject = subject,
                membershipQueueGroup = environment.nonBlankOrDefault("MEMBERSHIP_NATS_QUEUE_GROUP", subject),
                natsRequestTimeout = timeoutSeconds.seconds,
            )
        }
    }
}

private fun Array<String>.configFile(): Path? {
    if (isEmpty()) return null
    require(size == 2 && first() == "--config" && last().isNotBlank()) {
        "Application arguments must be empty or '--config <path>'"
    }

    return Path.of(last()).also { path ->
        require(Files.isRegularFile(path)) { "Configuration file does not exist: $path" }
    }
}

private fun readProperties(path: Path): Map<String, String> {
    val properties = Properties().apply {
        Files.newBufferedReader(path).use(::load)
    }
    return properties.stringPropertyNames().associateWith(properties::getProperty)
}

private fun Map<String, String>.requireNonBlank(name: String): String =
    requireNotNull(this[name]?.takeIf(String::isNotBlank)) { "$name must be configured" }

private fun Map<String, String>.nonBlankOrDefault(name: String, default: String): String =
    this[name]?.takeIf(String::isNotBlank) ?: default
