package netvaerke.application.web

import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining

internal data class ContactSubmission(
    val parameters: Parameters,
    val image: ContactImageUpload?,
    val removeImage: Boolean,
    val imageError: String?,
)

internal data class ContactImageUpload(
    val content: ByteArray,
    val contentType: String,
)

internal suspend fun ApplicationCall.receiveContactSubmission(): ContactSubmission {
    if (!request.contentType().match(ContentType.MultiPart.FormData)) {
        val parameters = receiveParameters()
        return ContactSubmission(
            parameters = parameters,
            image = null,
            removeImage = parameters["removeImage"] == "true",
            imageError = null,
        )
    }

    val parameters = ParametersBuilder()
    var image: ContactImageUpload? = null
    var imageError: String? = null
    receiveMultipart().forEachPart { part ->
        try {
            when (part) {
                is PartData.FormItem -> part.name?.let { parameters.append(it, part.value) }
                is PartData.FileItem -> if (part.name == "image" && !part.originalFileName.isNullOrBlank()) {
                    if (image != null || imageError != null) {
                        imageError = "Choose only one contact photo."
                    } else {
                        val bytes = part.provider().readRemaining(MAX_IMAGE_SIZE_BYTES + 1).toByteArray()
                        image = bytes.toContactImageUpload().also { upload ->
                            if (upload == null) imageError = imageValidationError(bytes)
                        }
                    }
                }

                else -> Unit
            }
        } finally {
            part.dispose()
        }
    }

    return ContactSubmission(
        parameters = parameters.build(),
        image = image,
        removeImage = parameters["removeImage"] == "true",
        imageError = imageError,
    )
}

private fun ByteArray.toContactImageUpload(): ContactImageUpload? = when {
    size.toLong() > MAX_IMAGE_SIZE_BYTES -> null
    isJpeg() -> ContactImageUpload(this, "image/jpeg")
    isPng() -> ContactImageUpload(this, "image/png")
    isWebp() -> ContactImageUpload(this, "image/webp")
    else -> null
}

private fun imageValidationError(bytes: ByteArray): String = when {
    bytes.size.toLong() > MAX_IMAGE_SIZE_BYTES -> "Contact photos must be 5 MB or smaller."
    else -> "Choose a JPEG, PNG, or WebP contact photo."
}

private fun ByteArray.isJpeg(): Boolean = size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

private fun ByteArray.isPng(): Boolean = size >= 8 &&
    this[0] == 0x89.toByte() && this[1] == 0x50.toByte() && this[2] == 0x4E.toByte() && this[3] == 0x47.toByte() &&
    this[4] == 0x0D.toByte() && this[5] == 0x0A.toByte() && this[6] == 0x1A.toByte() && this[7] == 0x0A.toByte()

private fun ByteArray.isWebp(): Boolean = size >= 12 &&
    this[0] == 'R'.code.toByte() && this[1] == 'I'.code.toByte() && this[2] == 'F'.code.toByte() && this[3] == 'F'.code.toByte() &&
    this[8] == 'W'.code.toByte() && this[9] == 'E'.code.toByte() && this[10] == 'B'.code.toByte() && this[11] == 'P'.code.toByte()

@Suppress("DEPRECATION")
private fun ByteReadPacket.toByteArray(): ByteArray = readBytes()

private const val MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024
