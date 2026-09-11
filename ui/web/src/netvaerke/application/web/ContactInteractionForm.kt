package netvaerke.application.web

import io.ktor.http.Parameters
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import netvaerke.manager.network.ContactInteractionDto
import netvaerke.manager.network.CreateContactInteractionDto
import netvaerke.manager.network.InteractionChannelDto
import netvaerke.manager.network.UpdateContactInteractionDto

internal data class ContactInteractionForm(
    val channel: String = InteractionChannelDto.PHONE.name,
    val notes: String = "",
    val occurredAt: String = Instant.now().toString(),
) {
    val occurredAtInput: String get() = occurredAt.toUtcLocalDateTime()

    fun validationError(): String? = when {
        channel.toInteractionChannel() == null -> "Choose an interaction type."
        occurredAt.toIsoInstant() == null -> "Enter when the interaction happened."
        else -> null
    }

    fun toCreateDto(): CreateContactInteractionDto = CreateContactInteractionDto(
        channel = checkNotNull(channel.toInteractionChannel()),
        notes = notes.takeIf(String::isNotBlank),
        occurredAt = checkNotNull(occurredAt.toIsoInstant()),
    )

    fun toUpdateDto(): UpdateContactInteractionDto = UpdateContactInteractionDto(
        channel = checkNotNull(channel.toInteractionChannel()),
        notes = notes.takeIf(String::isNotBlank),
        occurredAt = checkNotNull(occurredAt.toIsoInstant()),
    )
}

internal data class InteractionChannelOption(
    val value: String,
    val label: String,
)

internal fun interactionChannelOptions(): List<InteractionChannelOption> = InteractionChannelDto.entries.map { channel ->
    InteractionChannelOption(channel.name, channel.displayName())
}

internal fun Parameters.toContactInteractionForm(): ContactInteractionForm = ContactInteractionForm(
    channel = this["channel"].orEmpty(),
    notes = this["notes"]?.trim().orEmpty(),
    occurredAt = this["occurredAt"]?.trim().orEmpty(),
)

internal fun ContactInteractionDto.toContactInteractionForm(): ContactInteractionForm = ContactInteractionForm(
    channel = channel.name,
    notes = notes.orEmpty(),
    occurredAt = occurredAt,
)

internal fun InteractionChannelDto.displayName(): String = when (this) {
    InteractionChannelDto.PHONE -> "Phone"
    InteractionChannelDto.EMAIL -> "Email"
    InteractionChannelDto.TEXT -> "Text"
    InteractionChannelDto.CHAT -> "Chat"
    InteractionChannelDto.IN_PERSON -> "In person"
}

private fun String.toInteractionChannel(): InteractionChannelDto? =
    InteractionChannelDto.entries.firstOrNull { it.name == this }

private fun String.toIsoInstant(): String? = runCatching {
    Instant.parse(this)
}.recoverCatching {
    LocalDateTime.parse(this).toInstant(ZoneOffset.UTC)
}.getOrNull()?.toString()

private fun String.toUtcLocalDateTime(): String = runCatching {
    Instant.parse(this).atOffset(ZoneOffset.UTC).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}.getOrDefault("")
