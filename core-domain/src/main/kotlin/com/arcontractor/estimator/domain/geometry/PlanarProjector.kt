package com.arcontractor.estimator.domain.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Result of projecting a 3D vertex ring onto its best-fit 2D [PlanarBasis].
 *
 * @property basis Right-handed orthonormal basis `(origin, uAxis, vAxis, normal, orientation)`.
 * @property projectedVertices2D Planar `(u, v)` coordinates in meters corresponding 1-to-1 with [originalVertices3D].
 * @property originalVertices3D Input 3D world-space vertices in meters.
 * @property signedResidualsMeters Per-vertex perpendicular signed offset `w_i` from the plane in meters.
 * @property rmsPlanarErrorMeters Root-mean-square out-of-plane deviation (surface flatness quality metric).
 * @property maxPlanarDeviationMeters Maximum absolute out-of-plane deviation across all vertices.
 */
data class PlanarProjectionResult(
    val basis: PlanarBasis,
    val projectedVertices2D: List<Vec2>,
    val originalVertices3D: List<Vec3>,
    val signedResidualsMeters: List<Double>,
    val rmsPlanarErrorMeters: Double,
    val maxPlanarDeviationMeters: Double
) {
    /**
     * Industry flatness check (e.g., TCNA large-format tile tolerance is ~3-6 mm variation).
     * Flags surfaces with > 8 mm RMS planar deviation so contractors know leveling/skimming may be needed.
     */
    val isFlatWithinStandardTolerance: Boolean
        get() = rmsPlanarErrorMeters <= STANDARD_FLATNESS_RMS_TOLERANCE_METERS

    companion object {
        const val STANDARD_FLATNESS_RMS_TOLERANCE_METERS = 0.008
    }
}

/**
 * Projects 3D world-space polygon vertices lying on an arbitrary plane (horizontal floor,
 * vertical wall, or sloped ceiling) into a gravity-aligned orthonormal 2D planar basis `(u, v, n)`.
 */
object PlanarProjector {

    private const val MIN_VERTICES = 3
    private const val COLLINEAR_AREA_EPSILON = 1e-10
    private const val HORIZONTAL_DOT_THRESHOLD = 0.95
    private const val VERTICAL_DOT_THRESHOLD = 0.25

    /**
     * Computes a best-fit [PlanarBasis] from [vertices3D] ($N \ge 3$) using centroid-shifted Newell's method,
     * aligns `(u, v)` meaningfully to World Up / primary baseline, and projects all points to 2D.
     *
     * @param vertices3D Ordered 3D polygon vertices in meters ($N \ge 3$).
     * @param referenceNormal Optional outward surface normal (e.g., from ARCore hit-test) to disambiguate `+n` sign.
     */
    fun projectPolygon(
        vertices3D: List<Vec3>,
        referenceNormal: Vec3? = null
    ): PlanarProjectionResult {
        if (vertices3D.size < MIN_VERTICES) {
            throw InvalidPolygonException(
                "At least $MIN_VERTICES 3D vertices are required to define a plane, got ${vertices3D.size}"
            )
        }

        val basis = computeBasis(vertices3D, referenceNormal)
        return projectOntoBasis(vertices3D, basis)
    }

    /**
     * Projects additional 3D vertices (such as an interior window or door cutout) onto an **existing**
     * parent surface's [PlanarBasis], guaranteeing cutouts share the exact same `(u, v)` coordinate frame.
     */
    fun projectOntoBasis(
        vertices3D: List<Vec3>,
        basis: PlanarBasis
    ): PlanarProjectionResult {
        if (vertices3D.size < MIN_VERTICES) {
            throw InvalidPolygonException(
                "At least $MIN_VERTICES 3D vertices are required for planar projection, got ${vertices3D.size}"
            )
        }

        val projected = ArrayList<Vec2>(vertices3D.size)
        val residuals = ArrayList<Double>(vertices3D.size)
        var sumSqResidual = 0.0
        var maxAbsResidual = 0.0

        for (p in vertices3D) {
            projected.add(basis.projectTo2D(p))
            val w = basis.signedPlaneDistance(p)
            residuals.add(w)
            sumSqResidual += w * w
            maxAbsResidual = max(maxAbsResidual, abs(w))
        }

        val rmsError = sqrt(sumSqResidual / vertices3D.size.toDouble())

        return PlanarProjectionResult(
            basis = basis,
            projectedVertices2D = projected,
            originalVertices3D = vertices3D.toList(),
            signedResidualsMeters = residuals,
            rmsPlanarErrorMeters = rmsError,
            maxPlanarDeviationMeters = maxAbsResidual
        )
    }

    /**
     * Constructs a right-handed orthonormal [PlanarBasis] `(uAxis, vAxis, normal)` for [vertices3D].
     */
    fun computeBasis(
        vertices3D: List<Vec3>,
        referenceNormal: Vec3? = null
    ): PlanarBasis {
        if (vertices3D.size < MIN_VERTICES) {
            throw InvalidPolygonException(
                "At least $MIN_VERTICES vertices required to compute planar basis, got ${vertices3D.size}"
            )
        }

        val centroid = computeCentroid(vertices3D)
        var normal = computeNewellNormal(vertices3D, centroid)
            ?: computeFallbackTripletNormal(vertices3D)
            ?: throw DegenerateGeometryException(
                "3D vertices are collinear or coincident; cannot construct a valid plane normal"
            )

        // Align normal direction with ARCore's outward surface normal if provided
        val refUnit = referenceNormal?.normalizedOrNull()
        if (refUnit != null && normal.dot(refUnit) < 0.0) {
            normal = -normal
        }

        val upDot = normal.dot(Vec3.UNIT_Y)
        val orientation = classifyOrientation(upDot)

        val uAxis: Vec3
        val vAxis: Vec3

        if (abs(upDot) < HORIZONTAL_DOT_THRESHOLD) {
            // Vertical wall or sloped ceiling/ramp:
            // Align +vAxis with projected World Up (+Y) so blueprints stand upright,
            // and set +uAxis = (vAxis x normal) so uAxis x vAxis == normal.
            vAxis = Vec3.UNIT_Y.projectOntoPlane(normal).normalized()
            uAxis = vAxis.cross(normal).normalized()
        } else {
            // Horizontal floor or ceiling:
            // Align +uAxis with the first non-degenerate polygon edge projected onto the plane,
            // falling back to projected World +X. Set +vAxis = (normal x uAxis) so uAxis x vAxis == normal.
            val primaryEdge = findPrimaryInPlaneDirection(vertices3D, normal)
            uAxis = primaryEdge.normalized()
            vAxis = normal.cross(uAxis).normalized()
        }

        return PlanarBasis(
            origin = centroid,
            uAxis = uAxis,
            vAxis = vAxis,
            normal = normal,
            orientation = orientation
        )
    }

    private fun computeCentroid(points: List<Vec3>): Vec3 {
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        for (p in points) {
            sx += p.x
            sy += p.y
            sz += p.z
        }
        val invN = 1.0 / points.size.toDouble()
        return Vec3(sx * invN, sy * invN, sz * invN)
    }

    /**
     * Centroid-shifted Newell's method: computes area-weighted best-fit normal across all N vertices,
     * averaging out per-vertex AR depth jitter and tolerating collinear consecutive edge triplets.
     */
    private fun computeNewellNormal(points: List<Vec3>, centroid: Vec3): Vec3? {
        var nx = 0.0
        var ny = 0.0
        var nz = 0.0
        val n = points.size
        for (i in 0 until n) {
            val curr = points[i] - centroid
            val next = points[(i + 1) % n] - centroid
            nx += (curr.y - next.y) * (curr.z + next.z)
            ny += (curr.z - next.z) * (curr.x + next.x)
            nz += (curr.x - next.x) * (curr.y + next.y)
        }
        val raw = Vec3(nx, ny, nz)
        return if (raw.lengthSquared() <= COLLINEAR_AREA_EPSILON * COLLINEAR_AREA_EPSILON) {
            null
        } else {
            raw.normalized()
        }
    }

    private fun computeFallbackTripletNormal(points: List<Vec3>): Vec3? {
        val p0 = points[0]
        var bestCross = Vec3.ZERO
        var bestLenSq = 0.0
        val n = points.size
        for (i in 1 until n - 1) {
            val e1 = points[i] - p0
            for (j in i + 1 until n) {
                val e2 = points[j] - p0
                val cross = e1.cross(e2)
                val lenSq = cross.lengthSquared()
                if (lenSq > bestLenSq) {
                    bestLenSq = lenSq
                    bestCross = cross
                }
            }
        }
        return if (bestLenSq <= COLLINEAR_AREA_EPSILON * COLLINEAR_AREA_EPSILON) {
            null
        } else {
            bestCross.normalized()
        }
    }

    private fun findPrimaryInPlaneDirection(points: List<Vec3>, unitNormal: Vec3): Vec3 {
        val p0 = points[0]
        for (i in 1 until points.size) {
            val projected = (points[i] - p0).projectOntoPlane(unitNormal)
            if (projected.lengthSquared() > 1e-10) {
                return projected
            }
        }
        val fallbackAxis = if (abs(unitNormal.dot(Vec3.UNIT_X)) < 0.9) Vec3.UNIT_X else Vec3.UNIT_Z
        return fallbackAxis.projectOntoPlane(unitNormal)
    }

    private fun classifyOrientation(upDot: Double): SurfaceOrientation = when {
        upDot >= HORIZONTAL_DOT_THRESHOLD -> SurfaceOrientation.HORIZONTAL_FLOOR
        upDot <= -HORIZONTAL_DOT_THRESHOLD -> SurfaceOrientation.HORIZONTAL_CEILING
        abs(upDot) <= VERTICAL_DOT_THRESHOLD -> SurfaceOrientation.VERTICAL_WALL
        else -> SurfaceOrientation.SLOPED_SURFACE
    }
}
