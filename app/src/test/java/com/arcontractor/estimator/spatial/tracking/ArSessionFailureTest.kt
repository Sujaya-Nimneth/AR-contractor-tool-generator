package com.arcontractor.estimator.spatial.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class ArSessionFailureTest {
    @Test
    fun `unsupported device is terminal and directs user back to quote`() {
        val failure = UnavailableDeviceNotCompatibleException()
            .toArSessionFailure(context(cameraPermissionGranted = true))

        assertEquals(ArIssueKind.UNSUPPORTED_DEVICE, failure.kind)
        assertFalse(failure.canRetrySession)
        assertEquals(ArRecoveryAction.BACK_TO_QUOTE, failure.kind.recoveryAction())
    }

    @Test
    fun `declined installation is retryable after user installs ARCore`() {
        val failure = UnavailableUserDeclinedInstallationException()
            .toArSessionFailure(context(cameraPermissionGranted = true))

        assertEquals(ArIssueKind.INSTALL_REQUIRED, failure.kind)
        assertTrue(failure.canRetrySession)
        assertEquals(ArRecoveryAction.RETRY, failure.kind.recoveryAction())
    }

    @Test
    fun `missing camera permission opens app settings`() {
        val context = context(cameraPermissionGranted = false)

        val failure = IllegalStateException("Camera permission denied")
            .toArSessionFailure(context)

        assertEquals(ArIssueKind.CAMERA_PERMISSION, failure.kind)
        assertFalse(failure.canRetrySession)
        assertEquals(
            ArRecoveryAction.OPEN_CAMERA_SETTINGS,
            failure.kind.recoveryAction()
        )
        assertNotNull(ArRecoveryAction.OPEN_CAMERA_SETTINGS.intent(context))
    }

    @Test
    fun `unknown recoverable failure preserves the original message`() {
        val failure = IllegalStateException("Session interrupted")
            .toArSessionFailure(context(cameraPermissionGranted = true))

        assertEquals(ArIssueKind.RECOVERABLE, failure.kind)
        assertEquals("Session interrupted", failure.message)
        assertTrue(failure.canRetrySession)
    }

    private fun context(cameraPermissionGranted: Boolean): Context =
        object : android.content.ContextWrapper(null) {
            override fun checkSelfPermission(permission: String): Int =
                if (permission == Manifest.permission.CAMERA && cameraPermissionGranted) {
                    PackageManager.PERMISSION_GRANTED
                } else {
                    PackageManager.PERMISSION_DENIED
                }

            override fun getPackageName(): String = "com.arcontractor.estimator.test"
        }
}
