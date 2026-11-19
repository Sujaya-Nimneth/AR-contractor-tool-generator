package com.arcontractor.estimator.domain.geometry

/**
 * Orientation of a manually entered rectangular surface.
 */
enum class ManualSurfaceOrientation {
    /** Vertical wall: width runs horizontally, height runs along world up. */
    WALL,

    /** Horizontal floor: width and depth span the ground plane. */
    FLOOR
}

/**
 * One rectangular opening inside a manually entered surface.
 * Offsets are measured from the surface's bottom-left corner in meters.
 */
data class ManualCutoutSpec(
    val label: String,
    val kind: CutoutKind,
    val offsetHorizontalMeters: Double,
    val offsetVerticalMeters: Double,
    val widthMeters: Double,
    val heightMeters: Double
) {
    init {
        require(widthMeters > 0.0 && heightMeters > 0.0) { "Cutout dimensions must be positive" }
        require(offsetHorizontalMeters >= 0.0 && offsetVerticalMeters >= 0.0) {
            "Cutout offsets must be non-negative"
        }
    }
}

/**
 * Synthesizes planar 3D vertex rings for typed-dimension (non-AR) surface entry so manual
 * quotes flow through the exact same [PolygonGeometryEngine] analysis, persistence, and
 * estimation pipeline as AR measurements.
 *
 * Coordinates follow the project convention: right-handed Y-up world in meters. Walls lie
 * on the z = 0 plane facing +Z; floors lie on the y = 0 plane facing +Y.
 */
object ManualSurfaceBuilder {

    private const val MIN_DIMENSION_METERS = 0.01

    /**
     * Builds the outer boundary vertices and cutout polygons for a rectangular surface.
     *
     * @throws InvalidPolygonException when dimensions are too small or a cutout does not fit
     *         inside the surface boundary.
     */
    fun buildRectangularSurface(
        widthMeters: Double,
        heightMeters: Double,
        orientation: ManualSurfaceOrientation,
        cutouts: List<ManualCutoutSpec> = emptyList()
    ): ManualSurfaceGeometry {
        if (widthMeters < MIN_DIMENSION_METERS || heightMeters < MIN_DIMENSION_METERS) {
            throw InvalidPolygonException(
                "Surface dimensions must be at least ${MIN_DIMENSION_METERS} m"
            )
        }

        val outer = when (orientation) {
            ManualSurfaceOrientation.WALL -> listOf(
                Vec3(0.0, 0.0, 0.0),
                Vec3(widthMeters, 0.0, 0.0),
                Vec3(widthMeters, heightMeters, 0.0),
                Vec3(0.0, heightMeters, 0.0)
            )

            ManualSurfaceOrientation.FLOOR -> listOf(
                Vec3(0.0, 0.0, 0.0),
                Vec3(widthMeters, 0.0, 0.0),
                Vec3(widthMeters, 0.0, -heightMeters),
                Vec3(0.0, 0.0, -heightMeters)
            )
        }
        val referenceNormal = when (orientation) {
            ManualSurfaceOrientation.WALL -> Vec3.UNIT_Z
            ManualSurfaceOrientation.FLOOR -> Vec3.UNIT_Y
        }

        val cutoutInputs = cutouts.mapIndexed { index, cutout ->
            val right = cutout.offsetHorizontalMeters + cutout.widthMeters
            val top = cutout.offsetVerticalMeters + cutout.heightMeters
            if (right > widthMeters + 1e-9 || top > heightMeters + 1e-9) {
                throw InvalidPolygonException(
                    "Cutout '${cutout.label}' extends beyond the ${widthMeters} m × ${heightMeters} m surface"
                )
            }
            val corners2D = listOf(
                cutout.offsetHorizontalMeters to cutout.offsetVerticalMeters,
                right to cutout.offsetVerticalMeters,
                right to top,
                cutout.offsetHorizontalMeters to top
            )
            CutoutPolygonInput(
                id = "manual-cutout-${index + 1}",
                label = cutout.label,
                kind = cutout.kind,
                vertices3D = corners2D.map { (horizontal, vertical) ->
                    when (orientation) {
                        ManualSurfaceOrientation.WALL -> Vec3(horizontal, vertical, 0.0)
                        ManualSurfaceOrientation.FLOOR -> Vec3(horizontal, 0.0, -vertical)
                    }
                }
            )
        }

        return ManualSurfaceGeometry(
            outerVertices3D = outer,
            referenceNormal = referenceNormal,
            cutouts = cutoutInputs
        )
    }
}

/**
 * Synthetic measurement ready for `PolygonGeometryEngine.analyze3DSurface` and persistence.
 */
data class ManualSurfaceGeometry(
    val outerVertices3D: List<Vec3>,
    val referenceNormal: Vec3,
    val cutouts: List<CutoutPolygonInput>
)
