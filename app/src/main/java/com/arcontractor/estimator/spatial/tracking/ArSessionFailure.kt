package com.arcontractor.estimator.spatial.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.FatalException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.ar.core.exceptions.UnsupportedConfigurationException

enum class ArIssueKind {
    RECOVERABLE,
    CAMERA_PERMISSION,
    CAMERA_UNAVAILABLE,
    INSTALL_REQUIRED,
    UNSUPPORTED_DEVICE,
    UNSUPPORTED_CONFIGURATION
}

data class ArSessionFailure(
    val kind: ArIssueKind,
    val message: String,
    val canRetrySession: Boolean = true
)

fun Exception.toArSessionFailure(context: Context): ArSessionFailure = when {
    this is UnavailableDeviceNotCompatibleException ||
        this is UnavailableSdkTooOldException ||
        this is FatalException -> ArSessionFailure(
            kind = ArIssueKind.UNSUPPORTED_DEVICE,
            message = "This device cannot run ARCore measurement. Use manual quote entry instead.",
            canRetrySession = false
        )

    this is UnavailableArcoreNotInstalledException ||
        this is UnavailableApkTooOldException ||
        this is UnavailableUserDeclinedInstallationException -> ArSessionFailure(
            kind = ArIssueKind.INSTALL_REQUIRED,
            message = "Google Play Services for AR must be installed or updated before measuring.",
            canRetrySession = true
        )

    this is UnsupportedConfigurationException -> ArSessionFailure(
        kind = ArIssueKind.UNSUPPORTED_CONFIGURATION,
        message = "This device does not support the required AR depth configuration. Plane measurement is still available.",
        canRetrySession = true
    )

    this is CameraNotAvailableException -> ArSessionFailure(
        kind = ArIssueKind.CAMERA_UNAVAILABLE,
        message = "The camera is unavailable. Close other camera apps and try again.",
        canRetrySession = true
    )

    context.hasCameraPermission().not() -> ArSessionFailure(
        kind = ArIssueKind.CAMERA_PERMISSION,
        message = "Camera permission is required for AR measurement. Enable it in Android settings.",
        canRetrySession = false
    )

    else -> ArSessionFailure(
        kind = ArIssueKind.RECOVERABLE,
        message = message?.takeIf { it.isNotBlank() }
            ?: "ARCore could not start. Check camera access and try again.",
        canRetrySession = true
    )
}

internal fun Context.hasCameraPermission(): Boolean =
    checkSelfPermission(Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

fun ArIssueKind.recoveryAction(): ArRecoveryAction = when (this) {
    ArIssueKind.CAMERA_PERMISSION -> ArRecoveryAction.OPEN_CAMERA_SETTINGS
    ArIssueKind.UNSUPPORTED_DEVICE -> ArRecoveryAction.BACK_TO_QUOTE
    else -> ArRecoveryAction.RETRY
}

fun ArRecoveryAction.intent(context: Context): Intent? = when (this) {
    ArRecoveryAction.RETRY,
    ArRecoveryAction.BACK_TO_QUOTE -> null
    ArRecoveryAction.OPEN_CAMERA_SETTINGS -> Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

