package netvaerke.access.file

import kotlin.time.Duration

interface FileAccess {
    fun createFilePutUrl(fileId: String, bucket: String, expiry: Duration): String

    fun createFileGetUrl(fileId: String, bucket: String, expiry: Duration): String
}