package com.arcontractor.estimator.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfQuoteGeneratorInstrumentedTest {
    private val generator = PdfQuoteGenerator(FIXED_CLOCK)

    @Test
    fun generatorWritesRenderableA4AndLetterDocuments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDirectory = requireNotNull(
            context.getExternalFilesDir(PDF_VALIDATION_DIRECTORY) ?: context.filesDir
        )
        assertTrue(outputDirectory.exists() || outputDirectory.mkdirs())
        val workspace = fixtureWorkspace()

        listOf(
            PdfExportOptions(PdfPaperSize.A4, validityDays = 30),
            PdfExportOptions(PdfPaperSize.LETTER, validityDays = 45)
        ).forEach { options ->
            val outputFile = File(outputDirectory, "${options.paperSize.name.lowercase()}.pdf")
            val expectedPages = generator.pageCount(workspace, options)
            val generatedPages = generator.generate(workspace, options, outputFile)

            assertEquals(expectedPages, generatedPages)
            assertTrue(outputFile.length() > 10_000L)
            assertEquals(
                "%PDF-",
                outputFile.inputStream().use { it.readNBytes(5).decodeToString() }
            )
            assertRenderablePdf(outputFile, expectedPages)
        }
    }

    @Test
    fun exporterReturnsAReadableFileProviderUri() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exporter = AndroidQuotePdfExporter(
            context = context,
            generator = generator,
            clock = FIXED_CLOCK
        )
        val result = exporter.export(
            workspace = fixtureWorkspace(),
            options = PdfExportOptions(PdfPaperSize.A4)
        )

        assertTrue(result.file.isFile)
        assertTrue(result.file.canonicalPath.startsWith(context.filesDir.canonicalPath))
        assertEquals("content", result.contentUri.scheme)
        assertEquals("${context.packageName}.fileprovider", result.contentUri.authority)
        val header = context.contentResolver.openInputStream(result.contentUri).use { stream ->
            requireNotNull(stream).readNBytes(5).decodeToString()
        }
        assertEquals("%PDF-", header)
        assertRenderablePdf(result.file, result.pageCount)
    }

    private fun assertRenderablePdf(file: File, expectedPages: Int) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(expectedPages, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        page.width,
                        page.height,
                        Bitmap.Config.ARGB_8888
                    )
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        assertTrue(countNonWhiteSamples(bitmap) > 500)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
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

    private fun fixtureWorkspace(): ProjectWorkspace {
        val project = ProjectEntity(
            id = "instrumented-harbor-project",
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
        const val PDF_VALIDATION_DIRECTORY = "pdf-validation"

        val FIXED_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-10-31T00:00:00Z"),
            ZoneOffset.UTC
        )
    }
}
