package netvaerke.application.web

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class ApplicationConfig(
    val host: String,
    val port: Int,
    val natsUrl: String,
    val membershipSubject: String,
    val natsRequestTimeout: Duration,
    val hankoApiUrl: String,
    val hankoValidationApiUrl: String,
    val hankoCookieDomain: String?,
    val secureCookies: Boolean,
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
            val timeoutSeconds = environment.positiveLongOrDefault("MEMBERSHIP_NATS_TIMEOUT_SECONDS", 5)
            val port = environment.positiveIntOrDefault("PORT", 8080)
            val hankoApiUrl = environment.requireHankoApiUrl().trimEnd('/')

            return ApplicationConfig(
                host = environment.nonBlankOrDefault("HOST", "0.0.0.0"),
                port = port,
                natsUrl = environment.requireNonBlank("NATS_URL"),
                membershipSubject = environment.nonBlankOrDefault("MEMBERSHIP_NATS_SUBJECT", DEFAULT_MEMBERSHIP_SUBJECT),
                natsRequestTimeout = timeoutSeconds.seconds,
                hankoApiUrl = hankoApiUrl,
                hankoValidationApiUrl = environment.nonBlankOrDefault("HANKO_VALIDATION_API_URL", hankoApiUrl).trimEnd('/'),
                hankoCookieDomain = environment["HANKO_COOKIE_DOMAIN"]?.takeIf(String::isNotBlank),
                secureCookies = environment["SECURE_COOKIES"]?.toBooleanStrictOrNull() ?: hankoApiUrl.startsWith("https://"),
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

private fun Map<String, String>.requireHankoApiUrl(): String =
    requireNotNull(this["HANKO_API_URL"]?.takeIf(String::isNotBlank)) {
        "HANKO_API_URL must be configured. For local development, pass --config ui/web/config/local.properties"
    }

private fun Map<String, String>.nonBlankOrDefault(name: String, default: String): String =
    this[name]?.takeIf(String::isNotBlank) ?: default

private fun Map<String, String>.positiveLongOrDefault(name: String, default: Long): Long {
    val value = this[name]?.let {
        requireNotNull(it.toLongOrNull()) { "$name must be a positive integer" }
    } ?: default
    require(value > 0) { "$name must be a positive integer" }
    return value
}

private fun Map<String, String>.positiveIntOrDefault(name: String, default: Int): Int {
    val value = this[name]?.let {
        requireNotNull(it.toIntOrNull()) { "$name must be a positive integer" }
    } ?: default
    require(value > 0) { "$name must be a positive integer" }
    return value
}
