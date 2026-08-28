package netvaerke.application.web

import java.io.InputStream
import java.net.URI
import java.time.Duration as JavaDuration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import kotlin.time.Duration

/** Generic Garage object storage operations for the web application. */
internal interface FileStorage : AutoCloseable {
    suspend fun putFile(
        bucket: String,
        fileKey: String,
        contentType: String,
        contentLength: Long,
        content: InputStream,
    )

    fun createGetUrl(bucket: String, fileKey: String, expiry: Duration): String

    suspend fun deleteFile(bucket: String, fileKey: String)
}

internal class GarageFileStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileStorage {
    override suspend fun putFile(
        bucket: String,
        fileKey: String,
        contentType: String,
        contentLength: Long,
        content: InputStream,
    ) {
        requireStorageLocation(bucket, fileKey)
        require(contentType.isNotBlank()) { "Content type must not be blank" }
        require(contentLength >= 0) { "Content length must not be negative" }

        withContext(ioDispatcher) {
            s3Client.putObject(
                { request ->
                    request.bucket(bucket)
                        .key(fileKey)
                        .contentType(contentType)
                },
                RequestBody.fromInputStream(content, contentLength),
            )
        }
    }

    override fun createGetUrl(bucket: String, fileKey: String, expiry: Duration): String {
        requireStorageLocation(bucket, fileKey)
        require(expiry.isPositive()) { "URL expiry must be positive" }

        return s3Presigner.presignGetObject { request ->
            request.signatureDuration(expiry.toJavaDuration())
                .getObjectRequest { getObject ->
                    getObject.bucket(bucket).key(fileKey)
                }
        }.url().toString()
    }

    override suspend fun deleteFile(bucket: String, fileKey: String) {
        requireStorageLocation(bucket, fileKey)

        withContext(ioDispatcher) {
            s3Client.deleteObject { request ->
                request.bucket(bucket).key(fileKey)
            }
        }
    }

    override fun close() {
        s3Presigner.close()
        s3Client.close()
    }

    internal companion object {
        fun create(
            endpoint: URI,
            region: String,
            accessKey: String,
            secretKey: String,
        ): GarageFileStorage {
            require(region.isNotBlank()) { "S3 region must not be blank" }
            require(accessKey.isNotBlank()) { "S3 access key must not be blank" }
            require(secretKey.isNotBlank()) { "S3 secret key must not be blank" }

            val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            val s3Region = Region.of(region)
            return GarageFileStorage(
                s3Client = S3Client.builder()
                    .endpointOverride(endpoint)
                    .region(s3Region)
                    .credentialsProvider(credentials)
                    .forcePathStyle(true)
                    .build(),
                s3Presigner = S3Presigner.builder()
                    .endpointOverride(endpoint)
                    .region(s3Region)
                    .credentialsProvider(credentials)
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build(),
            )
        }
    }
}

private fun requireStorageLocation(bucket: String, fileKey: String) {
    require(bucket.isNotBlank()) { "Bucket must not be blank" }
    require(fileKey.isNotBlank()) { "File key must not be blank" }
}

private fun Duration.toJavaDuration(): JavaDuration =
    JavaDuration.ofSeconds(inWholeSeconds, inWholeNanoseconds % 1_000_000_000)
