package com.arcontractor.estimator.data.update

import android.content.Context
import android.os.Build
import com.arcontractor.estimator.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the quote workspace needs from the updater. Keeping it an interface lets the
 * ViewModel stay free of Android context and keeps tests trivial.
 */
interface UpdateGateway {
    suspend fun check(): UpdateCheckResult
    suspend fun download(): UpdateDownloadResult
    fun openReleasePage()
}

sealed interface UpdateDownloadResult {
    /** The APK is downloaded, checksum-verified and handed to the package installer. */
    data class Ready(val apkFile: File) : UpdateDownloadResult

    /** The download succeeded but "install unknown apps" must be granted first. */
    data object NeedsPermission : UpdateDownloadResult

    data class Failed(val reason: String) : UpdateDownloadResult
}

/**
 * Android-facing half of the in-app updater: resolves the manifest, picks the best ABI variant,
 * downloads it into the private cache and hands it to the system installer.
 *
 * Nothing here blocks the quote workflow; every failure is reported as a message and the app
 * keeps running with the currently installed build.
 */
class UpdateCoordinator(
    private val context: Context,
    private val checker: GitHubUpdateChecker = GitHubUpdateChecker(
        manifestUrl = GitHubUpdateChecker.latestManifestUrl(BuildConfig.GITHUB_REPO),
        installedVersionCode = BuildConfig.VERSION_CODE
    ),
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
) : UpdateGateway {
    private var pendingManifest: ReleaseManifest? = null

    override suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val result = checker.check()
        if (result is UpdateCheckResult.UpdateAvailable) {
            pendingManifest = result.manifest
        }
        result
    }

    override suspend fun download(): UpdateDownloadResult = withContext(Dispatchers.IO) {
        val manifest = pendingManifest
        if (manifest == null) {
            UpdateDownloadResult.Failed("Check for an update before downloading.")
        } else {
            val variant = manifest.bestVariant(supportedAbis)
            if (variant == null) {
                UpdateDownloadResult.Failed("This release does not publish a compatible APK.")
            } else {
                val target = File(context.cacheDir, "apk-updates/${variant.apkFileName}")
                val downloaded = checker.downloadAndVerify(variant, target)
                when {
                    downloaded.isFailure -> UpdateDownloadResult.Failed(
                        downloaded.exceptionOrNull()?.message
                            ?: "The update download could not be completed."
                    )

                    !ApkInstaller.canRequestPackageInstalls(context) -> {
                        ApkInstaller.openUnknownSourcesSettings(context)
                        UpdateDownloadResult.NeedsPermission
                    }

                    ApkInstaller.install(context, downloaded.getOrThrow()) ->
                        UpdateDownloadResult.Ready(downloaded.getOrThrow())

                    else -> UpdateDownloadResult.Failed(
                        "Android could not open the install prompt."
                    )
                }
            }
        }
    }

    override fun openReleasePage() {
        ApkInstaller.openReleasePage(
            context,
            GitHubUpdateChecker.releasesUrl(BuildConfig.GITHUB_REPO)
        )
    }

    fun clearCache() {
        File(context.cacheDir, "apk-updates").deleteRecursively()
    }
}
