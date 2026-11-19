package com.arcontractor.estimator.ui.ar

import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.Pose3D
import com.arcontractor.estimator.domain.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementStateCodecTest {

    @Test
    fun `closed surface with cutout survives encode-decode round trip`() {
        val outer = listOf(
            Vec3(0.0, 0.0, -2.0),
            Vec3(4.0, 0.0, -2.0),
            Vec3(4.0, 2.7, -2.0),
            Vec3(0.0, 2.7, -2.0)
        )
        val cutout = CutoutPolygonInput(
            id = "cutout-1",
            label = "Door",
            kind = CutoutKind.DOOR,
            vertices3D = listOf(
                Vec3(0.4, 0.0, -2.0),
                Vec3(1.3, 0.0, -2.0),
                Vec3(1.3, 2.1, -2.0),
                Vec3(0.4, 2.1, -2.0)
            )
        )
        val state = ArMeasureUiState(
            phase = ArMeasurePhase.PLACING_CUTOUT,
            referencePose = Pose3D(position = outer[0], normal = Vec3.UNIT_Z),
            outerVertices = outer,
            outerClosed = true,
            activeCutoutVertices = listOf(Vec3(2.0, 1.0, -2.0), Vec3(3.0, 1.0, -2.0)),
            selectedCutoutKind = CutoutKind.WINDOW,
            cutouts = listOf(cutout)
        )

        val encoded = MeasurementStateCodec.encode(state)
        assertNotNull(encoded)
        val restored = MeasurementStateCodec.decode(encoded)
        assertNotNull(restored)

        assertEquals(ArMeasurePhase.PLACING_CUTOUT, restored!!.phase)
        assertEquals(outer, restored.outerVertices)
        assertEquals(state.referencePose?.position, restored.referencePose?.position)
        assertEquals(state.referencePose?.normal, restored.referencePose?.normal)
        assertEquals(true, restored.outerClosed)
        assertEquals(state.activeCutoutVertices, restored.activeCutoutVertices)
        assertEquals(CutoutKind.WINDOW, restored.selectedCutoutKind)
        assertEquals(1, restored.cutouts.size)
        assertEquals(cutout.vertices3D, restored.cutouts.single().vertices3D)
        assertEquals(CutoutKind.DOOR, restored.cutouts.single().kind)
        // Analysis is deterministically recomputed, not persisted.
        assertNotNull(restored.surfaceBasis)
        assertEquals(8.91, restored.surfaceAnalysis?.netAreaSqMeters ?: -1.0, 1e-6)
    }

    @Test
    fun `empty measurement is not persisted`() {
        assertNull(MeasurementStateCodec.encode(ArMeasureUiState()))
    }

    @Test
    fun `corrupt persisted payloads decode to null instead of crashing`() {
        assertNull(MeasurementStateCodec.decode(null))
        assertNull(MeasurementStateCodec.decode(""))
        assertNull(MeasurementStateCodec.decode("{not json"))
        assertNull(MeasurementStateCodec.decode("""{"phase":"NO_SUCH_PHASE"}"""))
    }

    @Test
    fun `two-vertex draft restores without basis or analysis`() {
        val state = ArMeasureUiState(
            phase = ArMeasurePhase.PLACING_OUTER_BOUNDARY,
            referencePose = Pose3D(position = Vec3(0.0, 0.0, -2.0), normal = Vec3.UNIT_Z),
            outerVertices = listOf(Vec3(0.0, 0.0, -2.0), Vec3(2.0, 0.0, -2.0))
        )

        val restored = MeasurementStateCodec.decode(MeasurementStateCodec.encode(state))
        assertNotNull(restored)
        assertEquals(2, restored!!.outerVertices.size)
        assertNull(restored.surfaceBasis)
        assertNull(restored.surfaceAnalysis)
    }
}
