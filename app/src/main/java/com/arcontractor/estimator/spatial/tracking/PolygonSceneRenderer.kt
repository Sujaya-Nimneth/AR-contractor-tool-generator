package com.arcontractor.estimator.spatial.tracking

import com.arcontractor.estimator.domain.geometry.AnalyzedSurfacePolygon
import com.arcontractor.estimator.domain.geometry.Vec3
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.dot
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.PI

/**
 * Translates domain geometry into Filament nodes for AR scene rendering.
 *
 * Surface fill triangles come exclusively from [AnalyzedSurfacePolygon.netSurfaceMeshTriangles2D]
 * and are unprojected 2 mm above the measured plane to prevent z-fighting with the real wall/floor.
 */
class PolygonSceneRenderer(
    private val engine: Engine,
    materialLoader: MaterialLoader
) {
    private var lastStructuralSnapshot: SurfaceRenderSnapshot? = null
    private var lastTargetNodes: MutableList<Node>? = null
    private var previewNode: CylinderNode? = null

    private val outerVertexMaterial = materialLoader.createColorInstance(
        color = 0xFF23D5AB.toInt(),
        metallic = 0.0f,
        roughness = 0.30f,
        reflectance = 0.15f
    )
    private val edgeMaterial = materialLoader.createColorInstance(
        color = 0xFFFFB547.toInt(),
        metallic = 0.0f,
        roughness = 0.45f,
        reflectance = 0.10f
    )
    private val surfaceMaterial = materialLoader.createColorInstance(
        color = 0x6632D5C7,
        metallic = 0.0f,
        roughness = 0.72f,
        reflectance = 0.05f
    )
    private val cutoutMaterial = materialLoader.createColorInstance(
        color = 0xFFFF5A5F.toInt(),
        metallic = 0.0f,
        roughness = 0.35f,
        reflectance = 0.10f
    )
    private val previewMaterial = materialLoader.createColorInstance(
        color = 0xCCF4F7FA.toInt(),
        metallic = 0.0f,
        roughness = 0.50f,
        reflectance = 0.05f
    )

    /**
     * Replaces the scene nodes atomically after all replacement geometry has been constructed.
     *
     * @return true when the scene was updated, false when Filament rejected the geometry and the
     * previous scene was preserved.
     */
    fun render(
        snapshot: SurfaceRenderSnapshot,
        targetNodes: MutableList<Node>
    ): Boolean {
        val structuralSnapshot = snapshot.copy(previewPoint = null)
        if (structuralSnapshot != lastStructuralSnapshot || targetNodes !== lastTargetNodes) {
            val rebuilt = rebuildStructuralNodes(structuralSnapshot, targetNodes)
            if (!rebuilt) return false
            lastStructuralSnapshot = structuralSnapshot
            lastTargetNodes = targetNodes
        }
        updatePreview(snapshot, targetNodes)
        return true
    }

    private fun rebuildStructuralNodes(
        snapshot: SurfaceRenderSnapshot,
        targetNodes: MutableList<Node>
    ): Boolean {
        val replacement = ArrayList<Node>()
        return try {
            buildSurfaceFill(snapshot.surfaceAnalysis)?.let(replacement::add)
            addOuterGeometry(snapshot, replacement)
            addCompletedCutoutWireframes(snapshot.surfaceAnalysis, replacement)
            addActiveCutoutGeometry(snapshot.activeCutoutVertices, replacement)

            val previous = targetNodes.toList()
            targetNodes.clear()
            targetNodes.addAll(replacement)
            previewNode = null
            previous.forEach { node -> runCatching { node.destroy() } }
            true
        } catch (_: RuntimeException) {
            replacement.forEach { node -> runCatching { node.destroy() } }
            false
        }
    }

    fun clear(targetNodes: MutableList<Node>) {
        val previous = targetNodes.toList()
        targetNodes.clear()
        previous.forEach { node -> runCatching { node.destroy() } }
        previewNode = null
        lastStructuralSnapshot = null
        lastTargetNodes = null
    }

    private fun addOuterGeometry(
        snapshot: SurfaceRenderSnapshot,
        nodes: MutableList<Node>
    ) {
        snapshot.outerVertices.forEach { vertex ->
            nodes.add(createVertexNode(vertex, outerVertexMaterial, radius = VERTEX_RADIUS_METERS))
        }

        val measuredEdges = if (snapshot.outerClosed) {
            snapshot.surfaceAnalysis?.outerEdges?.map { it.start3D to it.end3D }
                ?: closedEdges(snapshot.outerVertices)
        } else {
            openEdges(snapshot.outerVertices)
        }
        measuredEdges.forEach { (start, end) ->
            nodes.add(createEdgeNode(start, end, EDGE_RADIUS_METERS, edgeMaterial))
        }

        if (!snapshot.outerClosed && snapshot.outerVertices.size >= 3) {
            nodes.add(
                createEdgeNode(
                    start = snapshot.outerVertices.last(),
                    end = snapshot.outerVertices.first(),
                    radiusMeters = PREVIEW_RADIUS_METERS,
                    material = previewMaterial
                )
            )
        }
    }

    private fun addCompletedCutoutWireframes(
        analysis: AnalyzedSurfacePolygon?,
        nodes: MutableList<Node>
    ) {
        analysis?.cutouts?.forEach { cutout ->
            cutout.edges.forEach { edge ->
                nodes.add(
                    createEdgeNode(
                        start = edge.start3D,
                        end = edge.end3D,
                        radiusMeters = CUTOUT_RADIUS_METERS,
                        material = cutoutMaterial
                    )
                )
            }
        }
    }

    private fun addActiveCutoutGeometry(
        vertices: List<Vec3>,
        nodes: MutableList<Node>
    ) {
        vertices.forEach { vertex ->
            nodes.add(createVertexNode(vertex, cutoutMaterial, radius = VERTEX_RADIUS_METERS))
        }
        openEdges(vertices).forEach { (start, end) ->
            nodes.add(
                createEdgeNode(
                    start = start,
                    end = end,
                    radiusMeters = CUTOUT_RADIUS_METERS,
                    material = cutoutMaterial
                )
            )
        }
    }

    private fun updatePreview(
        snapshot: SurfaceRenderSnapshot,
        targetNodes: MutableList<Node>
    ) {
        val preview = snapshot.previewPoint
        val anchor = snapshot.activeCutoutVertices.lastOrNull()
            ?: snapshot.outerVertices.lastOrNull()
        if (preview == null || anchor == null || preview.distanceTo(anchor) < MIN_EDGE_LENGTH_METERS) {
            previewNode?.let { node ->
                targetNodes.remove(node)
                runCatching { node.destroy() }
            }
            previewNode = null
            return
        }

        val existing = previewNode
        if (existing == null) {
            previewNode = createEdgeNode(
                start = anchor,
                end = preview,
                radiusMeters = PREVIEW_RADIUS_METERS,
                material = previewMaterial
            ).also(targetNodes::add)
        } else {
            val direction = preview - anchor
            existing.updateGeometry(
                radius = PREVIEW_RADIUS_METERS,
                height = direction.length().toFloat().coerceAtLeast(MIN_EDGE_LENGTH_METERS),
                center = Float3(0.0f, 0.0f, 0.0f),
                sideCount = 12
            )
            existing.position = anchor.lerp(preview, 0.5).toFloat3()
            existing.quaternion = rotationFromYAxis(direction)
        }
    }

    private fun buildSurfaceFill(analysis: AnalyzedSurfacePolygon?): Node? {
        if (analysis == null || analysis.netSurfaceMeshTriangles2D.isEmpty()) return null

        val vertices = ArrayList<Geometry.Vertex>(
            analysis.netSurfaceMeshTriangles2D.size * 3
        )
        analysis.netSurfaceMeshTriangles2D.forEach { triangle ->
            listOf(triangle.a, triangle.b, triangle.c).forEach { point2D ->
                vertices.add(
                    Geometry.Vertex(
                        position = analysis.basis
                            .unprojectTo3D(point2D, SURFACE_NORMAL_OFFSET_METERS)
                            .toFloat3(),
                        normal = analysis.basis.normal.toFloat3()
                    )
                )
            }
        }
        val geometry = Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES)
            .vertices(vertices)
            .indices(vertices.indices.toList())
            .build(engine)
        return GeometryNode(
            engine = engine,
            geometry = geometry,
            materialInstance = surfaceMaterial
        ).also { node ->
            node.isShadowCaster = false
            node.isShadowReceiver = false
        }
    }

    private fun createVertexNode(
        position: Vec3,
        material: MaterialInstance,
        radius: Float
    ): SphereNode = SphereNode(
        engine = engine,
        radius = radius,
        center = position.toFloat3(),
        stacks = 12,
        slices = 16,
        materialInstance = material
    ).also { node ->
        node.isShadowCaster = false
        node.isShadowReceiver = false
    }

    private fun createEdgeNode(
        start: Vec3,
        end: Vec3,
        radiusMeters: Float,
        material: MaterialInstance
    ): CylinderNode {
        val direction = end - start
        val length = direction.length().toFloat()
        val midpoint = start.lerp(end, 0.5)
        return CylinderNode(
            engine = engine,
            radius = radiusMeters,
            height = length.coerceAtLeast(MIN_EDGE_LENGTH_METERS),
            center = Float3(0.0f, 0.0f, 0.0f),
            sideCount = 12,
            materialInstance = material
        ).also { node ->
            node.position = midpoint.toFloat3()
            node.quaternion = rotationFromYAxis(direction)
            node.isShadowCaster = false
            node.isShadowReceiver = false
        }
    }

    private fun rotationFromYAxis(direction: Vec3): Quaternion {
        val normalizedDirection = direction.normalizedOrNull()
            ?: return Quaternion(0.0f, 0.0f, 0.0f, 1.0f)
        val target = normalizedDirection.toFloat3()
        val yAxis = Float3(0.0f, 1.0f, 0.0f)
        val alignment = dot(yAxis, target)
        if (alignment < -0.9999f) {
            return Quaternion.fromAxisAngle(Float3(1.0f, 0.0f, 0.0f), PI.toFloat())
        }
        val axis = cross(yAxis, target)
        return normalize(Quaternion(axis.x, axis.y, axis.z, 1.0f + alignment))
    }

    private fun openEdges(vertices: List<Vec3>): List<Pair<Vec3, Vec3>> {
        if (vertices.size < 2) return emptyList()
        return (0 until vertices.lastIndex).map { index ->
            vertices[index] to vertices[index + 1]
        }
    }

    private fun closedEdges(vertices: List<Vec3>): List<Pair<Vec3, Vec3>> {
        if (vertices.size < 2) return emptyList()
        return vertices.indices.map { index ->
            vertices[index] to vertices[(index + 1) % vertices.size]
        }
    }

    private fun Vec3.toFloat3() = Float3(
        x = x.toFloat(),
        y = y.toFloat(),
        z = z.toFloat()
    )

    private companion object {
        const val VERTEX_RADIUS_METERS = 0.018f
        const val EDGE_RADIUS_METERS = 0.005f
        const val CUTOUT_RADIUS_METERS = 0.0045f
        const val PREVIEW_RADIUS_METERS = 0.0035f
        const val SURFACE_NORMAL_OFFSET_METERS = 0.002
        const val MIN_EDGE_LENGTH_METERS = 0.0001f
    }
}
