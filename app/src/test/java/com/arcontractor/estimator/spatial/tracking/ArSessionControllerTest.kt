package com.arcontractor.estimator.spatial.tracking

import com.google.ar.core.Config
import com.google.ar.core.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArSessionControllerTest {
    @Test
    fun `supported depth enables automatic depth and plane detection`() {
        val session = object : Session() {
            override fun isDepthModeSupported(mode: Config.DepthMode): Boolean =
                mode == Config.DepthMode.AUTOMATIC
        }
        val controller = ArSessionController { _, _ -> }

        val config = Config::class.java.getDeclaredConstructor().apply {
            isAccessible = true
        }.newInstance() as Config
        val values = readConfig(controller, session, config)

        assertEquals(
            Config.DepthMode.AUTOMATIC,
            values.depthMode
        )
        assertEquals(
            Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            values.planeFindingMode
        )
        assertEquals(
            Config.InstantPlacementMode.DISABLED,
            values.instantPlacementMode
        )
        assertEquals(
            Config.FocusMode.AUTO,
            values.focusMode
        )
        assertTrue(controller.isDepthEnabled)
        assertNull(controller.depthStatusMessage)
    }

    @Test
    fun `unsupported depth disables depth and reports plane only fallback`() {
        val session = object : Session() {
            override fun isDepthModeSupported(mode: Config.DepthMode): Boolean = false
        }
        val controller = ArSessionController { _, _ -> }

        val config = Config::class.java.getDeclaredConstructor().apply {
            isAccessible = true
        }.newInstance() as Config
        val values = readConfig(controller, session, config)

        assertEquals(
            Config.DepthMode.DISABLED,
            values.depthMode
        )
        assertFalse(controller.isDepthEnabled)
        assertNotNull(controller.depthStatusMessage)
    }

    private fun readConfig(
        controller: ArSessionController,
        session: Session,
        config: Config
    ): ArSessionController.ArSessionConfig {
        controller.applyTo(session, config)
        return controller.appliedConfig!!
    }
}
