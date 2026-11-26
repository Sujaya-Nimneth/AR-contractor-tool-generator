package com.arcontractor.estimator.data.update

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One downloadable APK variant advertised by a release's `version.json`.
 */
@Serializable
data class UpdateVariant(
    val abi: String,
    val apkFileName: String,
    val apkSha256: String,
    val apkSizeBytes: Long = 0L,
    val downloadUrl: String
)

/**
 * Contents of `version.json` published alongside every GitHub Release.
 */
@Serializable
data class ReleaseManifest(
    val versionCode: Int,
    val versionName: String,
    val gitSha: String = "",
    val builtAtUtc: String = "",
    val apkFileName: String,
    val apkSha256: String,
    val apkSizeBytes: Long = 0L,
    val downloadUrl: String,
    val variants: List<UpdateVariant> = emptyList()
) {
    /** Prefers the device's own ABI so a phone does not download the universal APK. */
    fun bestVariant(supportedAbis: List<String>): UpdateVariant? =
        variants.firstOrNull { it.abi in supportedAbis }
            ?: variants.firstOrNull { it.abi == UNIVERSAL_ABI_NAME }
            ?: variants.firstOrNull()
            ?: UpdateVariant(
                abi = UNIVERSAL_ABI_NAME,
                apkFileName = apkFileName,
                apkSha256 = apkSha256,
                apkSizeBytes = apkSizeBytes,
                downloadUrl = downloadUrl
            )
}

/** ABI label used by the release workflow for the fat APK. */
const val UNIVERSAL_ABI_NAME = "universal"

/**
 * Result of asking GitHub whether a newer build exists.
 */
sealed interface UpdateCheckResult {
    /** The installed build is current (or newer, e.g. a locally built debug APK). */
    data object UpToDate : UpdateCheckResult

    /** A newer release is published. */
    data class UpdateAvailable(val manifest: ReleaseManifest) : UpdateCheckResult

    /** The check could not complete: offline, timeout, corrupt JSON, or non-200 response. */
    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * Minimal, dependency-free GitHub Releases update checker.
 *
 * It performs a single GET against the rolling `latest` release's `version.json`, so it never
 * transmits project data, quotes, or any user identifier. Every failure mode — offline, DNS,
 * timeout, HTTP error, malformed JSON — degrades to [UpdateCheckResult.Failed] and the app keeps
 * working exactly as before.
 */
class GitHubUpdateChecker(
    private val manifestUrl: String,
    private val installedVersionCode: Int,
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MS,
    private val customOpenStream: ((String) -> InputStream)? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun openStream(url: String): InputStream =
        customOpenStream?.invoke(url) ?: openHttpStream(url)

    suspend fun check(): UpdateCheckResult = runCatching {
        val body = openStream(manifestUrl).use { stream ->
            stream.bufferedReader().readText()
        }
        val manifest = json.decodeFromString<ReleaseManifest>(body)
        if (manifest.versionCode > installedVersionCode) {
            UpdateCheckResult.UpdateAvailable(manifest)
        } else {
            UpdateCheckResult.UpToDate
        }
    }.getOrElse { error ->
        UpdateCheckResult.Failed(error.message ?: "The update check could not complete.")
    }

    /**
     * Streams [variant] into [targetFile] and rejects it unless the SHA-256 matches the manifest.
     * A mismatch deletes the partial file so a corrupted download can never be installed.
     */
    fun downloadAndVerify(variant: UpdateVariant, targetFile: File): Result<File> = runCatching {
        targetFile.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        openStream(variant.downloadUrl).use { input ->
            targetFile.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        val actual = digest.digest().joinToString("") { byte ->
            "%02x".format(byte)
        }
        if (!actual.equals(variant.apkSha256, ignoreCase = true)) {
            targetFile.delete()
            error("The downloaded update did not match its published checksum.")
        }
        targetFile
    }

    private fun openHttpStream(url: String): InputStream {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, application/vnd.github+json")
        }
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            error("The update server responded with HTTP $status.")
        }
        return connection.inputStream
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000
        private const val BUFFER_SIZE = 8 * 1024

        fun latestManifestUrl(repository: String): String =
            "https://github.com/$repository/releases/download/latest/version.json"

        fun releasesUrl(repository: String): String =
            "https://github.com/$repository/releases/latest"
    }
}
