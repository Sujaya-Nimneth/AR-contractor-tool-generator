package com.arcontractor.estimator.spatial.tracking

import com.google.ar.core.ArCoreApk
import org.junit.Assert.assertEquals
import org.junit.Test

class ArReadinessTest {
    @Test
    fun `installed ARCore is ready`() {
        assertEquals(
            ArReadiness.READY,
            ArCoreApk.Availability.SUPPORTED_INSTALLED.toArReadiness()
        )
    }

    @Test
    fun `missing and outdated ARCore report installation in progress`() {
        assertEquals(
            ArReadiness.INSTALLING,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED.toArReadiness()
        )
        assertEquals(
            ArReadiness.INSTALLING,
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD.toArReadiness()
        )
    }

    @Test
    fun `unsupported device gives a non AR fallback action`() {
        val guidance = ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
            .toArReadiness()
            .guidance()

        assertEquals(ArReadiness.UNSUPPORTED_DEVICE, guidance.readiness)
        assertEquals(ArRecoveryAction.BACK_TO_QUOTE, guidance.recoveryAction)
    }

    @Test
    fun `transient availability states are retryable`() {
        listOf(
            ArCoreApk.Availability.UNKNOWN_CHECKING,
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT
        ).forEach { availability ->
            val guidance = availability.toArReadiness().guidance()
            assertEquals(ArReadiness.TEMPORARILY_UNAVAILABLE, guidance.readiness)
            assertEquals(ArRecoveryAction.RETRY, guidance.recoveryAction)
        }
    }
}
