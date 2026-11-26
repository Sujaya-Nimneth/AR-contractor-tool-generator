package com.arcontractor.estimator.data.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubUpdateCheckerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `a higher remote versionCode reports an available update`() = runTest {
        val checker = checker(installed = 150, remote = MANIFEST_V151)

        val result = checker.check()

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val manifest = (result as UpdateCheckResult.UpdateAvailable).manifest
        assertEquals(151, manifest.versionCode)
        assertEquals("1.0.51", manifest.versionName)
    }

    @Test
    fun `an equal or lower remote versionCode is up to date`() = runTest {
        assertTrue(
            checker(installed = 151, remote = MANIFEST_V151).check() is UpdateCheckResult.UpToDate
        )
        assertTrue(
            checker(installed = 200, remote = MANIFEST_V151).check() is UpdateCheckResult.UpToDate
        )
    }

    @Test
    fun `corrupt json fails without throwing`() = runTest {
        val result = checker(installed = 150, remote = "{ not json ]").check()

        assertTrue(result is UpdateCheckResult.Failed)
    }

    @Test
    fun `an offline network failure degrades to a failed check`() = runTest {
        val checker = GitHubUpdateChecker(
            manifestUrl = "https://example.invalid/version.json",
            installedVersionCode = 150,
            customOpenStream = { throw java.io.IOException("offline") }
        )

        val result = checker.check()

        assertTrue(result is UpdateCheckResult.Failed)
        assertTrue((result as UpdateCheckResult.Failed).reason.contains("offline"))
    }

    @Test
    fun `download rejects a payload whose checksum does not match`() {
        val target = File(temporaryFolder.newFolder("apk-updates"), "update.apk")
        val checker = GitHubUpdateChecker(
            manifestUrl = "https://example.invalid/version.json",
            installedVersionCode = 150,
            customOpenStream = { stream(APK_BYTES) }
        )
        val variant = UpdateVariant(
            abi = "arm64-v8a",
            apkFileName = "update.apk",
            apkSha256 = "deadbeef",
            downloadUrl = "https://example.invalid/update.apk"
        )

        val result = checker.downloadAndVerify(variant, target)

        assertTrue(result.isFailure)
        assertFalse("A corrupt download must be deleted", target.exists())
    }

    @Test
    fun `download accepts a payload with the published checksum`() {
        val target = File(temporaryFolder.newFolder("apk-updates"), "update.apk")
        val checker = GitHubUpdateChecker(
            manifestUrl = "https://example.invalid/version.json",
            installedVersionCode = 150,
            customOpenStream = { stream(APK_BYTES) }
        )
        val variant = UpdateVariant(
            abi = "arm64-v8a",
            apkFileName = "update.apk",
            apkSha256 = sha256(APK_BYTES),
            downloadUrl = "https://example.invalid/update.apk"
        )

        val result = checker.downloadAndVerify(variant, target)

        assertTrue(result.isSuccess)
        assertEquals(APK_BYTES.toList(), target.readBytes().toList())
    }

    @Test
    fun `variant selection prefers the device abi then the universal apk`() = runTest {
        val result = checker(installed = 150, remote = MANIFEST_V151).check()
        val manifest = (result as UpdateCheckResult.UpdateAvailable).manifest

        assertEquals(
            "arm64-v8a",
            manifest.bestVariant(listOf("arm64-v8a", "armeabi-v7a"))?.abi
        )
        assertEquals("universal", manifest.bestVariant(listOf("x86_64"))?.abi)
        assertEquals("universal", manifest.bestVariant(emptyList())?.abi)
    }

    private fun checker(installed: Int, remote: String) = GitHubUpdateChecker(
        manifestUrl = "https://example.invalid/version.json",
        installedVersionCode = installed,
        customOpenStream = { stream(remote.toByteArray()) }
    )

    private fun stream(bytes: ByteArray): InputStream = ByteArrayInputStream(bytes)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte)
        }

    private companion object {
        val APK_BYTES = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01, 0x02, 0x03, 0x04)

        val MANIFEST_V151 = """
            {
              "versionCode": 151,
              "versionName": "1.0.51",
              "gitSha": "abc1234",
              "builtAtUtc": "2026-11-26T06:00:00Z",
              "apkFileName": "ar-contractor-estimator-universal-latest.apk",
              "apkSha256": "deadbeef",
              "apkSizeBytes": 33000000,
              "downloadUrl": "https://example.invalid/universal.apk",
              "variants": [
                {
                  "abi": "arm64-v8a",
                  "apkFileName": "ar-contractor-estimator-arm64-v8a-latest.apk",
                  "apkSha256": "cafebabe",
                  "apkSizeBytes": 10000000,
                  "downloadUrl": "https://example.invalid/arm64.apk"
                },
                {
                  "abi": "universal",
                  "apkFileName": "ar-contractor-estimator-universal-latest.apk",
                  "apkSha256": "deadbeef",
                  "apkSizeBytes": 33000000,
                  "downloadUrl": "https://example.invalid/universal.apk"
                }
              ]
            }
        """.trimIndent()
    }
}
