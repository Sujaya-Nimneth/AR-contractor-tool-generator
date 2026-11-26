package com.arcontractor.estimator.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * Hands a verified update APK to Android's package installer.
 *
 * Returns false when the user has not granted "install unknown apps" for this app yet, in which
 * case the caller should fall back to opening the release page in a browser.
 */
object ApkInstaller {
    // minSdk is 26, so the install-unknown-apps API is always available.
    fun canRequestPackageInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun install(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (!canRequestPackageInstalls(context)) {
            openUnknownSourcesSettings(context)
            return false
        }
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openReleasePage(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
