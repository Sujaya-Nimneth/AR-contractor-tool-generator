package com.arcontractor.estimator.spatial.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class ArInstallCoordinatorTest {
    @Test
    fun `successful install reports AR ready`() {
        val guidance = ArInstallResult.INSTALLED.guidance()

        assertEquals(ArReadiness.READY, guidance.readiness)
        assertEquals(ArRecoveryAction.RETRY, guidance.recoveryAction)
    }

    @Test
    fun `requested and declined installs remain retryable`() {
        listOf(ArInstallResult.INSTALL_REQUESTED, ArInstallResult.DECLINED).forEach { result ->
            val guidance = result.guidance()

            assertEquals(ArReadiness.INSTALLING, guidance.readiness)
            assertEquals(ArRecoveryAction.RETRY, guidance.recoveryAction)
        }
    }

    @Test
    fun `install failures guide users back to manual quote entry`() {
        val guidance = ArInstallResult.FAILED.guidance()

        assertEquals(ArReadiness.INSTALLATION_UNAVAILABLE, guidance.readiness)
        assertEquals(ArRecoveryAction.BACK_TO_QUOTE, guidance.recoveryAction)
    }
}
