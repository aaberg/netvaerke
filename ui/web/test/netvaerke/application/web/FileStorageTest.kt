package netvaerke.application.web

import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.ZERO

class FileStorageTest {
    @Test
    fun `creates a path style signed download URL`() {
        val storage = storage()

        try {
            val url = URI.create(storage.createGetUrl("images", "tenants/acme/contact.png", 1.hours))

            assertEquals("http", url.scheme)
            assertEquals("files.example.test", url.host)
            assertEquals("/images/tenants/acme/contact.png", url.path)
            assertTrue(url.query.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"))
            assertTrue(url.query.contains("X-Amz-Expires=3600"))
            assertTrue(url.query.contains("X-Amz-Signature="))
        } finally {
            storage.close()
        }
    }

    @Test
    fun `rejects non positive signed URL expiries`() {
        val storage = storage()

        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                storage.createGetUrl("images", "tenant/contact.png", ZERO)
            }

            assertEquals("URL expiry must be positive", failure.message)
        } finally {
            storage.close()
        }
    }

    @Test
    fun `rejects invalid upload and delete locations before contacting Garage`() = runBlocking {
        val storage = storage()

        try {
            val uploadFailure = assertFailsWith<IllegalArgumentException> {
                storage.putFile(
                    bucket = "images",
                    fileKey = "tenant/contact.png",
                    contentType = "image/png",
                    contentLength = -1,
                    content = "image".byteInputStream(),
                )
            }
            val deleteFailure = assertFailsWith<IllegalArgumentException> {
                storage.deleteFile(bucket = "", fileKey = "tenant/contact.png")
            }

            assertEquals("Content length must not be negative", uploadFailure.message)
            assertEquals("Bucket must not be blank", deleteFailure.message)
        } finally {
            storage.close()
        }
    }

    private fun storage(): GarageFileStorage = GarageFileStorage.create(
        endpoint = URI.create("http://garage.example.test:3900"),
        publicEndpoint = URI.create("http://files.example.test:3900"),
        region = "garage",
        accessKey = "access-key",
        secretKey = "secret-key",
    )
}
