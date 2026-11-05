package com.arcontractor.estimator.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import com.arcontractor.estimator.data.local.ProjectEntity
import com.arcontractor.estimator.data.repository.ProjectWorkspace
import com.arcontractor.estimator.data.repository.SurfaceQuoteLine
import com.arcontractor.estimator.domain.estimation.DefaultMaterialCatalog
import com.arcontractor.estimator.domain.estimation.MaterialEstimationEngine
import com.arcontractor.estimator.domain.estimation.MaterialSpec
import com.arcontractor.estimator.domain.estimation.ProjectQuoteAggregator
import com.arcontractor.estimator.domain.estimation.ProjectSurfaceInput
import com.arcontractor.estimator.domain.estimation.TaxPolicy
import com.arcontractor.estimator.domain.geometry.CutoutKind
import com.arcontractor.estimator.domain.geometry.CutoutPolygonInput
import com.arcontractor.estimator.domain.geometry.PolygonGeometryEngine
import com.arcontractor.estimator.domain.geometry.Vec3
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class PdfQuoteGeneratorTest {
    @Test
    fun `every A4 and Letter page renders complete native canvas content`() {
        val generator = PdfQuoteGenerator(FIXED_CLOCK)
        val previewDirectory = File("build/pdf-preview").apply {
            check(exists() || mkdirs()) { "Could not create PDF preview directory." }
        }
        val workspace = fixtureWorkspace()

        val a4Pages = renderPreviewSet(
            generator = generator,
            workspace = workspace,
            options = PdfExportOptions(PdfPaperSize.A4, validityDays = 30),
            label = "a4",
            outputDirectory = previewDirectory
        )
        val letterPages = renderPreviewSet(
            generator = generator,
            workspace = workspace,
            options = PdfExportOptions(PdfPaperSize.LETTER, validityDays = 45),
            label = "letter",
            outputDirectory = previewDirectory
        )

        assertTrue("Expected cover, blueprints, line items, and terms", a4Pages >= 7)
        assertEquals(a4Pages, letterPages)
    }

    @Test
    fun `more than eight surfaces produce a complete paginated summary`() {
        val workspace = fixtureWorkspace()
        val extraSurfaces = (1..7).map { index ->
            workspace.surfaces.first().copy(
                surfaceId = "extra-$index",
                surfaceName = "Additional surface $index"
            )
        }
        val surfaces = workspace.surfaces + extraSurfaces
        val quote = ProjectQuoteAggregator.aggregateProject(
            projectName = workspace.project.name,
            surfaces = surfaces.map { surface ->
                ProjectSurfaceInput(
                    surfaceId = surface.surfaceId,
                    surfaceName = surface.surfaceName,
                    geometry = requireNotNull(surface.analysis),
                    materialSpec = surface.effectiveMaterial
                )
            },
            taxRate = workspace.project.taxRate,
            taxPolicy = TaxPolicy.TAX_TOTAL_SUBTOTAL
        )
        val reconciledSurfaces = surfaces.map { surface ->
            surface.copy(
                estimate = quote.surfaces.first {
                    it.surfaceId == surface.surfaceId
                }.estimate
            )
        }
        val largeWorkspace = workspace.copy(
            surfaces = reconciledSurfaces,
            quote = quote
        )
        val generator = PdfQuoteGenerator(FIXED_CLOCK)
        val options = PdfExportOptions(PdfPaperSize.A4)

        val pageCount = generator.pageCount(largeWorkspace, options)

        assertTrue(pageCount >= 13)
        assertEquals(
            quote.grandTotal,
            reconciledSurfaces.mapNotNull(SurfaceQuoteLine::estimate)
                .fold(BigDecimal.ZERO) { acc, estimate ->
                    acc + estimate.grandTotal
                }.setScale(2, RoundingMode.HALF_UP)
        )
        repeat(pageCount) { index ->
            val bitmap = Bitmap.createBitmap(
                options.paperSize.widthPoints * PREVIEW_SCALE,
                options.paperSize.heightPoints * PREVIEW_SCALE,
                Bitmap.Config.ARGB_8888
            )
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.scale(PREVIEW_SCALE.toFloat(), PREVIEW_SCALE.toFloat())
                generator.renderPagePreview(
                    workspace = largeWorkspace,
                    options = options,
                    pageNumber = index + 1,
                    canvas = canvas
                )
                assertTrue(countNonWhiteSamples(bitmap) > MIN_NON_WHITE_SAMPLES)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun renderPreviewSet(
        generator: PdfQuoteGenerator,
        workspace: ProjectWorkspace,
        options: PdfExportOptions,
        label: String,
        outputDirectory: File
    ): Int {
        val pageCount = generator.pageCount(workspace, options)
        repeat(pageCount) { pageIndex ->
            val bitmap = Bitmap.createBitmap(
                options.paperSize.widthPoints * PREVIEW_SCALE,
                options.paperSize.heightPoints * PREVIEW_SCALE,
                Bitmap.Config.ARGB_8888
            )
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.scale(PREVIEW_SCALE.toFloat(), PREVIEW_SCALE.toFloat())
                val renderedPageCount = generator.renderPagePreview(
                    workspace = workspace,
                    options = options,
                    pageNumber = pageIndex + 1,
                    canvas = canvas
                )
                assertEquals(pageCount, renderedPageCount)
                assertTrue(
                    "$label page ${pageIndex + 1} should contain visible content",
                    countNonWhiteSamples(bitmap) > MIN_NON_WHITE_SAMPLES
                )
                assertWhitePageBorder(bitmap, "$label page ${pageIndex + 1}")

                val outputFile = File(
                    outputDirectory,
                    "$label-page-${(pageIndex + 1).toString().padStart(2, '0')}.png"
                )
                outputFile.outputStream().use { stream ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                assertTrue(outputFile.length() > 2_000L)
            } finally {
                bitmap.recycle()
            }
        }
        return pageCount
    }

    private fun countNonWhiteSamples(bitmap: Bitmap): Int {
        val step = 4
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) != 0 &&
                    (Color.red(pixel) < 248 || Color.green(pixel) < 248 || Color.blue(pixel) < 248)
                ) {
                    count++
                }
                x += step
            }
            y += step
        }
        return count
    }

    private fun assertWhitePageBorder(bitmap: Bitmap, label: String) {
        val border = 3
        for (x in 0 until bitmap.width step 3) {
            assertTrue("$label has ink at the top edge", bitmap.getPixel(x, border) == Color.WHITE)
            assertTrue(
                "$label has ink at the bottom edge",
                bitmap.getPixel(x, bitmap.height - border - 1) == Color.WHITE
            )
        }
        for (y in 0 until bitmap.height step 3) {
            assertTrue("$label has ink at the left edge", bitmap.getPixel(border, y) == Color.WHITE)
            assertTrue(
                "$label has ink at the right edge",
                bitmap.getPixel(bitmap.width - border - 1, y) == Color.WHITE
            )
        }
    }

    private fun fixtureWorkspace(): ProjectWorkspace {
        val project = ProjectEntity(
            id = "harbor-project",
            name = "Harbor Street renovation",
            clientName = "Morgan & Lee",
            contractorName = "Northline Build Co.",
            contractorContact = "hello@northline.example | +1 555 0142",
            contractorLicense = "GC-10482",
            taxRate = 0.0825,
            taxPolicy = TaxPolicy.TAX_TOTAL_SUBTOTAL.name,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L
        )
        val surfaces = listOf(
            surface(
                id = "bath-wall",
                name = "Primary bath east wall",
                vertices = wallVertices(width = 4.2, height = 2.8),
                material = DefaultMaterialCatalog.porcelainTile300x600Straight,
                cutouts = listOf(
                    CutoutPolygonInput(
                        id = "door",
                        label = "Door",
                        kind = CutoutKind.DOOR,
                        vertices3D = rectangleOnWall(0.28, 0.0, 1.18, 2.12)
                    )
                ),
                normal = Vec3(0.0, 0.0, 1.0)
            ),
            surface(
                id = "hall-floor",
                name = "Hall floor",
                vertices = listOf(
                    Vec3(0.0, 0.0, 0.0),
                    Vec3(5.4, 0.0, 0.0),
                    Vec3(5.4, 0.0, 4.1),
                    Vec3(0.0, 0.0, 4.1)
                ),
                material = DefaultMaterialCatalog.oakLuxuryVinylPlank,
                normal = Vec3(0.0, 1.0, 0.0)
            ),
            surface(
                id = "studio-wall",
                name = "Studio feature wall",
                vertices = wallVertices(width = 3.6, height = 2.65),
                material = DefaultMaterialCatalog.acrylicEggshellInteriorPaint,
                cutouts = listOf(
                    CutoutPolygonInput(
                        id = "window",
                        label = "Window",
                        kind = CutoutKind.WINDOW,
                        vertices3D = rectangleOnWall(1.1, 0.82, 2.2, 2.05)
                    )
                ),
                normal = Vec3(0.0, 0.0, 1.0)
            )
        )
        val quote = ProjectQuoteAggregator.aggregateProject(
            projectName = project.name,
            surfaces = surfaces.map { surface ->
                ProjectSurfaceInput(
                    surfaceId = surface.surfaceId,
                    surfaceName = surface.surfaceName,
                    geometry = requireNotNull(surface.analysis),
                    materialSpec = surface.effectiveMaterial
                )
            },
            taxRate = project.taxRate,
            taxPolicy = TaxPolicy.TAX_TOTAL_SUBTOTAL
        )
        return ProjectWorkspace(
            project = project,
            materials = DefaultMaterialCatalog.allPresets(),
            surfaces = surfaces,
            quote = quote,
            loadErrors = emptyList()
        )
    }

    private fun surface(
        id: String,
        name: String,
        vertices: List<Vec3>,
        material: MaterialSpec,
        normal: Vec3,
        cutouts: List<CutoutPolygonInput> = emptyList()
    ): SurfaceQuoteLine {
        val analysis = PolygonGeometryEngine.analyze3DSurface(
            outerVertices3D = vertices,
            cutouts = cutouts,
            referenceNormal = normal
        )
        val estimate = MaterialEstimationEngine.estimateSurface(
            geometry = analysis,
            materialSpec = material,
            taxRate = 0.0825,
            taxPolicy = TaxPolicy.TAX_TOTAL_SUBTOTAL
        )
        return SurfaceQuoteLine(
            surfaceId = id,
            surfaceName = name,
            materialId = material.id,
            outerVertices = vertices,
            referenceNormal = normal,
            cutouts = cutouts,
            effectiveMaterial = material,
            wastePresetOverride = null,
            customWasteFactorOverride = null,
            laborRateOverride = null,
            analysis = analysis,
            estimate = estimate
        )
    }

    private fun wallVertices(width: Double, height: Double): List<Vec3> = listOf(
        Vec3(0.0, 0.0, 0.0),
        Vec3(width, 0.0, 0.0),
        Vec3(width, height, 0.0),
        Vec3(0.0, height, 0.0)
    )

    private fun rectangleOnWall(
        left: Double,
        bottom: Double,
        right: Double,
        top: Double
    ): List<Vec3> = listOf(
        Vec3(left, bottom, 0.001),
        Vec3(right, bottom, 0.001),
        Vec3(right, top, 0.001),
        Vec3(left, top, 0.001)
    )

    private companion object {
        const val PREVIEW_SCALE = 2
        const val MIN_NON_WHITE_SAMPLES = 500

        val FIXED_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-10-31T00:00:00Z"),
            ZoneOffset.UTC
        )
    }
}
