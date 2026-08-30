package netvaerke.application.web

import io.ktor.http.Parameters
import netvaerke.businesslogic.network.CreateNewContactDto
import netvaerke.businesslogic.network.EmailAddressDto
import netvaerke.businesslogic.network.NoteDto
import netvaerke.businesslogic.network.PhoneNumberDto
import netvaerke.businesslogic.network.TenantContactDto
import netvaerke.businesslogic.network.UpdateContactDto
import netvaerke.businesslogic.network.WorkInfoDto

internal data class ContactForm(
    val name: String = "",
    val emails: List<ContactEmailForm> = listOf(ContactEmailForm()),
    val phoneNumbers: List<ContactPhoneForm> = listOf(ContactPhoneForm()),
    val workTitle: String = "",
    val workOrganization: String = "",
    val note: String = "",
    val imageUrl: String? = null,
) {
    val primaryEmailIndex: Int? get() = emails.indexOfFirst(ContactEmailForm::isPrimary).takeIf { it >= 0 }

    fun validationError(): String? = when {
        name.isBlank() -> "Enter a contact name."
        name.length > 255 -> "A contact name must be 255 characters or fewer."
        emails.any { it.value.isBlank() && it.label.isNotBlank() } -> "Enter an email address or remove its label."
        emails.any { it.value.length > 255 || (it.value.isNotBlank() && !EMAIL_PATTERN.matches(it.value)) } ->
            "Enter valid email addresses."
        phoneNumbers.any { it.value.isBlank() && it.label.isNotBlank() } -> "Enter a phone number or remove its label."
        else -> null
    }

    fun toCreateDto(): CreateNewContactDto = CreateNewContactDto(
        name = name,
        emails = emailAddresses(),
        phoneNumbers = phoneNumbers(),
        workInfo = workInfo(),
        note = note(),
    )

    fun toUpdateDto(): UpdateContactDto = UpdateContactDto(
        name = name,
        emails = emailAddresses(),
        phoneNumbers = phoneNumbers(),
        workInfo = workInfo(),
        note = note(),
    )

    private fun emailAddresses(): List<EmailAddressDto> = emails
        .filter { it.value.isNotBlank() }
        .map { EmailAddressDto(value = it.value, isPrimary = it.isPrimary, label = it.label.ifBlank { null }) }

    private fun phoneNumbers(): List<PhoneNumberDto> = phoneNumbers
        .filter { it.value.isNotBlank() }
        .map { PhoneNumberDto(value = it.value, label = it.label.ifBlank { null }) }

    private fun workInfo(): WorkInfoDto? =
        if (workTitle.isBlank() && workOrganization.isBlank()) null else WorkInfoDto(
            title = workTitle.ifBlank { null },
            organization = workOrganization.ifBlank { null },
        )

    private fun note(): NoteDto? = note.takeIf(String::isNotBlank)?.let(::NoteDto)
}

internal data class ContactEmailForm(
    val value: String = "",
    val label: String = "",
    val isPrimary: Boolean = false,
)

internal data class ContactPhoneForm(
    val value: String = "",
    val label: String = "",
)

internal fun Parameters.toContactForm(): ContactForm {
    val primaryEmailIndex = this["emailPrimary"]?.toIntOrNull()
    return ContactForm(
        name = this["name"]?.trim().orEmpty(),
        emails = contactIndexes("emailValue-").map { index ->
            ContactEmailForm(
                value = this["emailValue-$index"]?.trim().orEmpty(),
                label = this["emailLabel-$index"]?.trim().orEmpty(),
                isPrimary = primaryEmailIndex == index,
            )
        }.ifEmpty { listOf(ContactEmailForm()) },
        phoneNumbers = contactIndexes("phoneValue-").map { index ->
            ContactPhoneForm(
                value = this["phoneValue-$index"]?.trim().orEmpty(),
                label = this["phoneLabel-$index"]?.trim().orEmpty(),
            )
        }.ifEmpty { listOf(ContactPhoneForm()) },
        workTitle = this["workTitle"]?.trim().orEmpty(),
        workOrganization = this["workOrganization"]?.trim().orEmpty(),
        note = this["note"]?.trim().orEmpty(),
    )
}

internal fun TenantContactDto.toContactForm(imageUrl: String?): ContactForm = ContactForm(
    name = name,
    emails = emails.map { ContactEmailForm(it.value, it.label.orEmpty(), it.isPrimary) }
        .ifEmpty { listOf(ContactEmailForm()) },
    phoneNumbers = phoneNumbers.map { ContactPhoneForm(it.value, it.label.orEmpty()) }
        .ifEmpty { listOf(ContactPhoneForm()) },
    workTitle = workInfo?.title.orEmpty(),
    workOrganization = workInfo?.organization.orEmpty(),
    note = note?.value.orEmpty(),
    imageUrl = imageUrl,
)

private fun Parameters.contactIndexes(prefix: String): List<Int> = names()
    .mapNotNull { name -> name.removePrefix(prefix).takeIf { name.startsWith(prefix) }?.toIntOrNull() }
    .sorted()

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
