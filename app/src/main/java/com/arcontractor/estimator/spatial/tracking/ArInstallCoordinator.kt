package com.arcontractor.estimator.spatial.tracking

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk

enum class ArInstallResult {
    INSTALLED,
    INSTALL_REQUESTED,
    DECLINED,
    FAILED
}

fun Context.requestArInstall(userRequestedInstall: Boolean = true): ArInstallResult =
    (this as? Activity)?.let { activity ->
        runCatching {
            ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)
        }.fold(
            onSuccess = { status ->
                when (status) {
                    ArCoreApk.InstallStatus.INSTALLED -> ArInstallResult.INSTALLED
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> ArInstallResult.INSTALL_REQUESTED
                }
            },
            onFailure = { error ->
                when (error) {
                    is com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException ->
                        ArInstallResult.DECLINED
                    else -> ArInstallResult.FAILED
                }
            }
        )
    } ?: ArInstallResult.FAILED

fun ArInstallResult.guidance(): ArReadinessGuidance = when (this) {
    ArInstallResult.INSTALLED -> ArReadiness.READY.guidance()
    ArInstallResult.INSTALL_REQUESTED,
    ArInstallResult.DECLINED -> ArReadinessGuidance(
        readiness = ArReadiness.INSTALLING,
        message = "Google Play Services for AR is installing or updating. Return to this screen when it finishes.",
        recoveryAction = ArRecoveryAction.RETRY
    )
    ArInstallResult.FAILED -> ArReadinessGuidance(
        readiness = ArReadiness.INSTALLATION_UNAVAILABLE,
        message = "Google Play Services for AR is unavailable. Check the Play Store or use manual quote entry.",
        recoveryAction = ArRecoveryAction.BACK_TO_QUOTE
    )
}

fun Context.resolveArReadiness(requestInstall: Boolean = true): ArReadinessGuidance {
    val readiness = checkArReadiness()
    return if (requestInstall && readiness == ArReadiness.INSTALLING && hasCameraPermission()) {
        requestArInstall().guidance()
    } else {
        readiness.guidance()
    }
}
