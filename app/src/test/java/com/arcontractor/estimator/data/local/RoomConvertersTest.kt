package com.arcontractor.estimator.data.local

import com.arcontractor.estimator.domain.estimation.DefaultMaterialCatalog
import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun `vertex list and normal round trip without precision loss`() {
        val vertices = listOf(
            Vec3(1.25, -2.5, 3.75),
            Vec3(0.000001, 42.125, -0.0)
        )
        val normal = Vec3(0.0, 1.0, -0.0)

        assertEquals(vertices, converters.jsonToVertices(converters.verticesToJson(vertices)))
        assertEquals(normal, converters.jsonToNormal(converters.normalToJson(normal)))
        assertNull(converters.normalToJson(null))
        assertNull(converters.jsonToNormal(null))
    }

    @Test
    fun `material mapping preserves catalog pricing and trade parameters`() {
        val original = DefaultMaterialCatalog.porcelainTile300x600Straight

        val restored = original.toEntity(updatedAtEpochMillis = 1234L).toDomain()

        assertEquals(original, restored)
    }

    @Test
    fun `cutout mapping scopes ids to the owning surface and preserves order`() {
        val input = CutoutPolygonInput(
            id = "cutout-1",
            label = "Window",
            vertices3D = listOf(
                Vec3(0.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3(1.0, 1.0, 0.0)
            ),
            kind = CutoutKind.WINDOW
        )

        val entity = input.toEntity(surfaceId = "surface-a", displayOrder = 2)
        val restored = entity.toDomain()

        assertEquals("surface-a-cutout-3", entity.id)
        assertEquals(2, entity.displayOrder)
        assertEquals(input.vertices3D, restored.vertices3D)
        assertEquals(input.label, restored.label)
        assertEquals(input.kind, restored.kind)
    }
}
