package com.arcontractor.estimator.domain.geometry

import kotlin.math.abs
import kotlin.math.max

/**
 * Semantic category of an interior/boundary surface cutout.
 * Enables trade-specific estimation adjustments (e.g., bullnose trim around windows vs. baseboard deduction at doors).
 */
enum class CutoutKind {
    WINDOW,
    DOOR,
    VENT,
    ISLAND_OR_FIXTURE,
    OTHER
}

/**
 * Input specification for a 3D cutout polygon lying on the parent surface plane.
 */
data class CutoutPolygonInput(
    val id: String,
    val label: String,
    val vertices3D: List<Vec3>,
    val kind: CutoutKind = CutoutKind.OTHER
)

/**
 * Input specification for a 2D planar cutout polygon.
 */
data class CutoutPolygon2DInput(
    val id: String,
    val label: String,
    val vertices2D: List<Vec2>,
    val kind: CutoutKind = CutoutKind.OTHER
)

/**
 * Controls how cutout polygons that extend past the parent boundary are handled.
 */
enum class CutoutValidationPolicy {
    /**
     * Requires cutouts to lie inside or boundary-flush with the parent surface
     * (allows up to 2% area tolerance for minor AR aiming overshoot at floor/door edges).
     * Rejects cutouts placed outside the parent polygon.
     */
    STRICT_INTERIOR_OR_FLUSH,

    /**
     * Automatically clips protruding cutouts to the parent polygon boundary and subtracts
     * only the interior overlapping region.
     */
    CLIP_TO_PARENT
}

enum class CutoutContainmentStatus {
    FULLY_CONTAINED,
    BOUNDARY_FLUSH_OR_CLIPPED,
    OUTSIDE_PARENT
}

/**
 * Core geometric metrics contract consumed by `domain.estimation`.
 */
interface SurfaceGeometryMetrics {
    val grossAreaSqMeters: Double
    val cutoutAreaSqMeters: Double
    val netAreaSqMeters: Double
    val outerPerimeterMeters: Double
    val cutoutPerimeterMeters: Double
}

/**
 * Lightweight immutable implementation of [SurfaceGeometryMetrics] for direct estimation queries.
 */
data class BasicSurfaceGeometry(
    override val grossAreaSqMeters: Double,
    override val cutoutAreaSqMeters: Double = 0.0,
    override val netAreaSqMeters: Double = max(0.0, grossAreaSqMeters - cutoutAreaSqMeters),
    override val outerPerimeterMeters: Double,
    override val cutoutPerimeterMeters: Double = 0.0
) : SurfaceGeometryMetrics {
    init {
        require(grossAreaSqMeters >= 0.0) { "grossAreaSqMeters must be non-negative" }
        require(cutoutAreaSqMeters >= 0.0) { "cutoutAreaSqMeters must be non-negative" }
        require(netAreaSqMeters >= 0.0) { "netAreaSqMeters must be non-negative" }
        require(outerPerimeterMeters >= 0.0) { "outerPerimeterMeters must be non-negative" }
        require(cutoutPerimeterMeters >= 0.0) { "cutoutPerimeterMeters must be non-negative" }
    }
}

/**
 * Complete geometric analysis of a single cutout polygon projected onto its parent's [PlanarBasis].
 */
data class AnalyzedCutout(
    val id: String,
    val label: String,
    val kind: CutoutKind,
    val vertices3D: List<Vec3>,
    val vertices2D: List<Vec2>,
    val edges: List<EdgeSegment>,
    val rawAreaSqMeters: Double,
    val effectiveSubtractedAreaSqMeters: Double,
    val perimeter2DMeters: Double,
    val perimeter3DMeters: Double,
    val containmentStatus: CutoutContainmentStatus
)

/**
 * Complete 3D & 2D planar geometric analysis of a measured contractor surface, including
 * orthonormal basis, Shoelace gross/net areas, boolean cutout subtraction, edge segments,
 * flatness RMS diagnostics, and hole-punched 2D triangles for 3D rendering and PDF blueprints.
 */
data class AnalyzedSurfacePolygon(
    val basis: PlanarBasis,
    val outerVertices3D: List<Vec3>,
    val outerVertices2D: List<Vec2>,
    val outerEdges: List<EdgeSegment>,
    val boundingBox2D: BoundingBox2D,
    val cutouts: List<AnalyzedCutout>,
    override val grossAreaSqMeters: Double,
    override val cutoutAreaSqMeters: Double,
    override val netAreaSqMeters: Double,
    override val outerPerimeterMeters: Double,
    val outerPerimeter3DMeters: Double,
    override val cutoutPerimeterMeters: Double,
    val cutoutPerimeter3DMeters: Double,
    val rmsPlanarErrorMeters: Double,
    val maxPlanarDeviationMeters: Double,
    val hadOverlappingCutouts: Boolean,
    val netSurfaceMeshTriangles2D: List<Triangle2D>
) : SurfaceGeometryMetrics {

    val grossAreaSqFeet: Double
        get() = UnitConverter.sqMetersToSqFeet(grossAreaSqMeters)

    val cutoutAreaSqFeet: Double
        get() = UnitConverter.sqMetersToSqFeet(cutoutAreaSqMeters)

    val netAreaSqFeet: Double
        get() = UnitConverter.sqMetersToSqFeet(netAreaSqMeters)

    val outerPerimeterFeet: Double
        get() = UnitConverter.metersToFeet(outerPerimeterMeters)

    val cutoutPerimeterFeet: Double
        get() = UnitConverter.metersToFeet(cutoutPerimeterMeters)
}

/**
 * High-precision 3D-to-2D Planar Polygon & Boolean Cutout Geometry Engine.
 */
object PolygonGeometryEngine {

    private const val MIN_POLYGON_AREA_SQ_METERS = 1e-4 // 1 sq cm minimum physical polygon
    private const val STRICT_PROTRUSION_RATIO_TOLERANCE = 0.02 // 2% AR edge tap tolerance
    private const val CONTAINMENT_AREA_EPSILON = 1e-5

    /**
     * Projects an arbitrary 3D surface polygon ($N \ge 3$) and optional coplanar 3D cutouts onto
     * a gravity-aligned orthonormal [PlanarBasis], computes exact Shoelace areas, boolean cutout
     * subtraction, edge lengths, total/cutout perimeters, and hole-punched mesh triangles.
     *
     * @param outerVertices3D Ordered 3D world-space vertices of the parent surface ($N \ge 3$).
     * @param cutouts Optional interior or boundary-flush 3D cutouts (doors, windows, vents, islands).
     * @param referenceNormal Optional outward surface normal from ARCore to orient `+normal` toward viewer.
     * @param cutoutPolicy Validation behavior when a cutout extends beyond the parent polygon boundary.
     * @param coplanarityConfig Optional coplanarity gate enforced on cutout vertices relative to the parent plane.
     */
    fun analyze3DSurface(
        outerVertices3D: List<Vec3>,
        cutouts: List<CutoutPolygonInput> = emptyList(),
        referenceNormal: Vec3? = null,
        cutoutPolicy: CutoutValidationPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH,
        coplanarityConfig: CoplanarityConfig? = CoplanarityConfig(
            maxNormalAngleDegrees = 15.0,
            maxOrthogonalDistanceMeters = 0.10
        )
    ): AnalyzedSurfacePolygon {
        val cleanedOuter3D = removeSequentialDuplicates3D(outerVertices3D)
        if (cleanedOuter3D.size < 3) {
            throw InvalidPolygonException(
                "Outer surface boundary requires at least 3 distinct 3D vertices, got ${cleanedOuter3D.size}"
            )
        }

        val projection = PlanarProjector.projectPolygon(
            vertices3D = cleanedOuter3D,
            referenceNormal = referenceNormal
        )
        val basis = projection.basis

        val (ccwOuter2D, ccwOuter3D) = orientPairCounterClockwise(
            vertices2D = projection.projectedVertices2D,
            vertices3D = projection.originalVertices3D
        )

        validateSimplePolygon2D(ccwOuter2D, label = "Outer surface boundary")

        val grossArea = PolygonClipping2D.shoelaceArea(ccwOuter2D)
        val outerEdges = buildEdges(ccwOuter2D, ccwOuter3D)
        val outerPerim2D = outerEdges.sumOf { it.length2DMeters }
        val outerPerim3D = outerEdges.sumOf { it.length3DMeters }

        val coplanarityValidator = coplanarityConfig?.let { CoplanarityValidator(it) }
        val analyzedCutouts = ArrayList<AnalyzedCutout>(cutouts.size)
        val validCutoutPolys2D = ArrayList<List<Vec2>>(cutouts.size)

        for (cutout in cutouts) {
            val cleanedCutout3D = removeSequentialDuplicates3D(cutout.vertices3D)
            if (cleanedCutout3D.size < 3) {
                throw InvalidPolygonException(
                    "Cutout '${cutout.label}' requires at least 3 distinct 3D vertices, got ${cleanedCutout3D.size}"
                )
            }

            if (coplanarityValidator != null) {
                for ((idx, vertex) in cleanedCutout3D.withIndex()) {
                    val check = coplanarityValidator.validateAgainstBasis(basis, vertex)
                    if (!check.isCoplanar) {
                        throw InvalidPolygonException(
                            "Cutout '${cutout.label}' vertex #$idx lies ${
                                String.format("%.3f", check.orthogonalDistanceMeters)
                            } m off the parent surface plane (max allowed ${coplanarityConfig.maxOrthogonalDistanceMeters} m)"
                        )
                    }
                }
            }

            val cutoutProj = PlanarProjector.projectOntoBasis(cleanedCutout3D, basis)
            val (ccwCut2D, ccwCut3D) = orientPairCounterClockwise(
                vertices2D = cutoutProj.projectedVertices2D,
                vertices3D = cutoutProj.originalVertices3D
            )

            validateSimplePolygon2D(ccwCut2D, label = "Cutout '${cutout.label}'")

            val rawCutoutArea = PolygonClipping2D.shoelaceArea(ccwCut2D)
            val overlapWithParent = PolygonClipping2D.intersectionArea(ccwOuter2D, ccwCut2D)
            val outsideArea = max(0.0, rawCutoutArea - overlapWithParent)
            val outsideRatio = outsideArea / rawCutoutArea

            val status = when {
                overlapWithParent <= CONTAINMENT_AREA_EPSILON -> CutoutContainmentStatus.OUTSIDE_PARENT
                outsideArea <= CONTAINMENT_AREA_EPSILON -> CutoutContainmentStatus.FULLY_CONTAINED
                else -> CutoutContainmentStatus.BOUNDARY_FLUSH_OR_CLIPPED
            }

            if (cutoutPolicy == CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH) {
                if (status == CutoutContainmentStatus.OUTSIDE_PARENT) {
                    throw InvalidPolygonException(
                        "Cutout '${cutout.label}' lies completely outside the parent surface boundary"
                    )
                }
                if (outsideRatio > STRICT_PROTRUSION_RATIO_TOLERANCE) {
                    throw InvalidPolygonException(
                        "Cutout '${cutout.label}' protrudes ${
                            String.format("%.1f%%", outsideRatio * 100.0)
                        } outside the parent surface boundary"
                    )
                }
            }

            val cutEdges = buildEdges(ccwCut2D, ccwCut3D)
            val cutPerim2D = cutEdges.sumOf { it.length2DMeters }
            val cutPerim3D = cutEdges.sumOf { it.length3DMeters }

            analyzedCutouts.add(
                AnalyzedCutout(
                    id = cutout.id,
                    label = cutout.label,
                    kind = cutout.kind,
                    vertices3D = ccwCut3D,
                    vertices2D = ccwCut2D,
                    edges = cutEdges,
                    rawAreaSqMeters = rawCutoutArea,
                    effectiveSubtractedAreaSqMeters = overlapWithParent,
                    perimeter2DMeters = cutPerim2D,
                    perimeter3DMeters = cutPerim3D,
                    containmentStatus = status
                )
            )
            if (overlapWithParent > CONTAINMENT_AREA_EPSILON) {
                validCutoutPolys2D.add(ccwCut2D)
            }
        }

        // Exact boolean subtraction across all cutouts simultaneously (handles overlapping cutouts safely)
        val remainingConvexPieces = PolygonClipping2D.subtractCutoutsExact(
            parentPolygon = ccwOuter2D,
            cutoutPolygons = validCutoutPolys2D
        )
        val netArea = remainingConvexPieces.sumOf { PolygonClipping2D.shoelaceArea(it) }
            .coerceIn(0.0, grossArea)
        val totalEffectiveCutoutArea = max(0.0, grossArea - netArea)

        val sumIndividualOverlaps = analyzedCutouts.sumOf { it.effectiveSubtractedAreaSqMeters }
        val hadOverlaps = (sumIndividualOverlaps - totalEffectiveCutoutArea) > 1e-4

        val totalCutoutPerim2D = analyzedCutouts.sumOf { it.perimeter2DMeters }
        val totalCutoutPerim3D = analyzedCutouts.sumOf { it.perimeter3DMeters }

        val meshTriangles2D = PolygonClipping2D.fanTriangulateConvexPieces(remainingConvexPieces)

        return AnalyzedSurfacePolygon(
            basis = basis,
            outerVertices3D = ccwOuter3D,
            outerVertices2D = ccwOuter2D,
            outerEdges = outerEdges,
            boundingBox2D = BoundingBox2D.fromPoints(ccwOuter2D),
            cutouts = analyzedCutouts,
            grossAreaSqMeters = grossArea,
            cutoutAreaSqMeters = totalEffectiveCutoutArea,
            netAreaSqMeters = netArea,
            outerPerimeterMeters = outerPerim2D,
            outerPerimeter3DMeters = outerPerim3D,
            cutoutPerimeterMeters = totalCutoutPerim2D,
            cutoutPerimeter3DMeters = totalCutoutPerim3D,
            rmsPlanarErrorMeters = projection.rmsPlanarErrorMeters,
            maxPlanarDeviationMeters = projection.maxPlanarDeviationMeters,
            hadOverlappingCutouts = hadOverlaps,
            netSurfaceMeshTriangles2D = meshTriangles2D
        )
    }

    /**
     * Convenience entry point for analyzing already-projected 2D planar polygons (lies on standard XY plane).
     */
    fun analyze2DPolygon(
        outerVertices2D: List<Vec2>,
        cutouts2D: List<CutoutPolygon2DInput> = emptyList(),
        cutoutPolicy: CutoutValidationPolicy = CutoutValidationPolicy.STRICT_INTERIOR_OR_FLUSH
    ): AnalyzedSurfacePolygon {
        val outer3D = outerVertices2D.map { Vec3(it.u, it.v, 0.0) }
        val cutouts3D = cutouts2D.map { c ->
            CutoutPolygonInput(
                id = c.id,
                label = c.label,
                kind = c.kind,
                vertices3D = c.vertices2D.map { Vec3(it.u, it.v, 0.0) }
            )
        }
        return analyze3DSurface(
            outerVertices3D = outer3D,
            cutouts = cutouts3D,
            referenceNormal = Vec3.UNIT_Z,
            cutoutPolicy = cutoutPolicy
        )
    }

    /**
     * Computes sequential 3D Euclidean segment lengths for an open or closed polyline.
     */
    fun computeSegmentLengths3D(vertices3D: List<Vec3>, closed: Boolean = true): List<Double> {
        if (vertices3D.size < 2) return emptyList()
        val count = if (closed) vertices3D.size else vertices3D.size - 1
        val lengths = ArrayList<Double>(count)
        for (i in 0 until count) {
            lengths.add(vertices3D[i].distanceTo(vertices3D[(i + 1) % vertices3D.size]))
        }
        return lengths
    }

    private fun validateSimplePolygon2D(vertices2D: List<Vec2>, label: String) {
        if (vertices2D.size < 3) {
            throw InvalidPolygonException("$label must have at least 3 distinct vertices")
        }
        if (PolygonClipping2D.hasSelfIntersection(vertices2D)) {
            throw InvalidPolygonException("$label has self-intersecting or overlapping edges")
        }
        val area = PolygonClipping2D.shoelaceArea(vertices2D)
        if (area < MIN_POLYGON_AREA_SQ_METERS) {
            throw DegenerateGeometryException(
                "$label area (${String.format("%.6f", area)} m²) is below minimum threshold ($MIN_POLYGON_AREA_SQ_METERS m²)"
            )
        }
    }

    private fun orientPairCounterClockwise(
        vertices2D: List<Vec2>,
        vertices3D: List<Vec3>
    ): Pair<List<Vec2>, List<Vec3>> {
        require(vertices2D.size == vertices3D.size) {
            "2D and 3D vertex counts must match (${vertices2D.size} != ${vertices3D.size})"
        }
        return if (PolygonClipping2D.signedArea(vertices2D) < 0.0) {
            vertices2D.asReversed() to vertices3D.asReversed()
        } else {
            vertices2D to vertices3D
        }
    }

    private fun buildEdges(vertices2D: List<Vec2>, vertices3D: List<Vec3>): List<EdgeSegment> {
        val n = vertices2D.size
        val edges = ArrayList<EdgeSegment>(n)
        for (i in 0 until n) {
            val next = (i + 1) % n
            edges.add(
                EdgeSegment(
                    index = i,
                    start2D = vertices2D[i],
                    end2D = vertices2D[next],
                    start3D = vertices3D[i],
                    end3D = vertices3D[next]
                )
            )
        }
        return edges
    }

    private fun removeSequentialDuplicates3D(
        points: List<Vec3>,
        tolerance: Double = 1e-5
    ): List<Vec3> {
        if (points.isEmpty()) return emptyList()
        val result = ArrayList<Vec3>(points.size)
        for (p in points) {
            if (result.isEmpty() || result.last().distanceTo(p) > tolerance) {
                result.add(p)
            }
        }
        if (result.size > 1 && result.first().distanceTo(result.last()) <= tolerance) {
            result.removeAt(result.lastIndex)
        }
        return result
    }
}
