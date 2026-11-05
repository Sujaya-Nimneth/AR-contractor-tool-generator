package com.arcontractor.estimator.spatial.tracking

import android.content.Context
import com.google.ar.core.ArCoreApk

enum class ArReadiness {
    READY,
    INSTALLING,
    UNSUPPORTED_DEVICE,
    INSTALLATION_UNAVAILABLE,
    TEMPORARILY_UNAVAILABLE
}

enum class ArRecoveryAction {
    RETRY,
    OPEN_CAMERA_SETTINGS,
    BACK_TO_QUOTE
}

data class ArReadinessGuidance(
    val readiness: ArReadiness,
    val message: String,
    val recoveryAction: ArRecoveryAction
)

fun ArReadiness.guidance(): ArReadinessGuidance = when (this) {
    ArReadiness.READY -> ArReadinessGuidance(
        readiness = this,
        message = "AR measurement is ready.",
        recoveryAction = ArRecoveryAction.RETRY
    )
    ArReadiness.INSTALLING -> ArReadinessGuidance(
        readiness = this,
        message = "Google Play Services for AR is installing or updating. Return to this screen when it finishes.",
        recoveryAction = ArRecoveryAction.RETRY
    )
    ArReadiness.UNSUPPORTED_DEVICE -> ArReadinessGuidance(
        readiness = this,
        message = "This device does not support ARCore measurement. Use manual quote entry instead.",
        recoveryAction = ArRecoveryAction.BACK_TO_QUOTE
    )
    ArReadiness.INSTALLATION_UNAVAILABLE -> ArReadinessGuidance(
        readiness = this,
        message = "Google Play Services for AR is unavailable. Check the Play Store or use manual quote entry.",
        recoveryAction = ArRecoveryAction.BACK_TO_QUOTE
    )
    ArReadiness.TEMPORARILY_UNAVAILABLE -> ArReadinessGuidance(
        readiness = this,
        message = "ARCore availability is still being checked. Try again in a moment.",
        recoveryAction = ArRecoveryAction.RETRY
    )
}

fun ArCoreApk.Availability.toArReadiness(): ArReadiness = when (this) {
    ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArReadiness.READY
    ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
    ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArReadiness.INSTALLING
    ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArReadiness.UNSUPPORTED_DEVICE
    ArCoreApk.Availability.UNKNOWN_ERROR -> ArReadiness.INSTALLATION_UNAVAILABLE
    ArCoreApk.Availability.UNKNOWN_CHECKING,
    ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> ArReadiness.TEMPORARILY_UNAVAILABLE
}

fun Context.checkArReadiness(): ArReadiness =
    ArCoreApk.getInstance().checkAvailability(this).toArReadiness()
