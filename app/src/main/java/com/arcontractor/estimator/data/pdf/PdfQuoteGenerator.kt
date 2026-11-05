package com.arcontractor.estimator.data.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withTranslation
import com.arcontractor.estimator.data.repository.ProjectWorkspace
import com.arcontractor.estimator.data.repository.SurfaceQuoteLine
import com.arcontractor.estimator.domain.estimation.ProjectQuoteSummary
import com.arcontractor.estimator.domain.estimation.QuoteLineItem
import com.arcontractor.estimator.domain.estimation.TaxPolicy
import com.arcontractor.estimator.domain.geometry.UnitConverter
import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * Builds a multi-page, fully vector contractor quote PDF using Android's PdfDocument.
 */
class PdfQuoteGenerator(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val blueprintRenderer: BlueprintCanvasRenderer = BlueprintCanvasRenderer()
) {
    fun generate(
        workspace: ProjectWorkspace,
        options: PdfExportOptions,
        outputFile: File
    ): Int {
        val pagePlan = buildPagePlan(workspace, options)

        val document = PdfDocument()
        try {
            pagePlan.specs.forEachIndexed { index, spec ->
                val pageInfo = PdfDocument.PageInfo.Builder(
                    options.paperSize.widthPoints,
                    options.paperSize.heightPoints,
                    index + 1
                ).create()
                val page = document.startPage(pageInfo)
                val context = createPageContext(
                    canvas = page.canvas,
                    workspace = workspace,
                    options = options,
                    pagePlan = pagePlan,
                    pageNumber = index + 1
                )
                drawPage(context, spec)
                drawFooter(context)
                document.finishPage(page)
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use(document::writeTo)
            return pagePlan.specs.size
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            document.close()
        }
    }

    internal fun pageCount(
        workspace: ProjectWorkspace,
        options: PdfExportOptions
    ): Int = buildPagePlan(workspace, options).specs.size

    /**
     * Renders one production page onto a native Android Canvas for preview and visual regression tests.
     */
    internal fun renderPagePreview(
        workspace: ProjectWorkspace,
        options: PdfExportOptions,
        pageNumber: Int,
        canvas: Canvas
    ): Int {
        val pagePlan = buildPagePlan(workspace, options)
        require(pageNumber in 1..pagePlan.specs.size) {
            "Page number $pageNumber is outside 1..${pagePlan.specs.size}."
        }
        val context = createPageContext(
            canvas = canvas,
            workspace = workspace,
            options = options,
            pagePlan = pagePlan,
            pageNumber = pageNumber
        )
        drawPage(context, pagePlan.specs[pageNumber - 1])
        drawFooter(context)
        return pagePlan.specs.size
    }

    private fun buildPagePlan(
        workspace: ProjectWorkspace,
        options: PdfExportOptions
    ): PagePlan {
        require(workspace.surfaces.isNotEmpty()) {
            "Measure at least one surface before exporting a quote."
        }
        require(workspace.surfaces.all { it.analysis != null && it.estimate != null }) {
            "Fix surfaces with missing geometry before exporting the quote."
        }

        val quoteDate = LocalDate.now(clock)
        val tableRows = buildTableRows(workspace)
        val rowChunks = PdfQuoteFormat.chunkForPages(tableRows, ROWS_PER_TABLE_PAGE)
        val validSurfaces = workspace.surfaces.filter { it.analysis != null && it.estimate != null }
        val surfaceChunks = PdfQuoteFormat.chunkForPages(
            validSurfaces,
            SURFACES_PER_SUMMARY_PAGE
        )
        val pageSpecs = buildList {
            add(
                PageSpec.Cover(
                    surfaces = surfaceChunks.firstOrNull().orEmpty(),
                    pageIndex = 1,
                    pageCount = surfaceChunks.size
                )
            )
            surfaceChunks.drop(1).forEachIndexed { index, surfaces ->
                add(
                    PageSpec.SurfaceSummary(
                        surfaces = surfaces,
                        pageIndex = index + 2,
                        pageCount = surfaceChunks.size
                    )
                )
            }
            validSurfaces.forEachIndexed { index, surface ->
                add(PageSpec.Blueprint(surface, index + 1, validSurfaces.size))
            }
            rowChunks.forEachIndexed { index, rows ->
                add(
                    PageSpec.LineItems(
                        rows = rows,
                        showTotals = index == rowChunks.lastIndex
                    )
                )
            }
            add(PageSpec.Terms)
        }
        return PagePlan(
            quoteDate = quoteDate,
            validUntil = quoteDate.plusDays(options.validityDays.toLong()),
            quoteNumber = PdfQuoteFormat.quoteNumber(workspace.project.id, quoteDate),
            specs = pageSpecs
        )
    }

    private fun createPageContext(
        canvas: Canvas,
        workspace: ProjectWorkspace,
        options: PdfExportOptions,
        pagePlan: PagePlan,
        pageNumber: Int
    ): PageContext = PageContext(
        canvas = canvas,
        width = options.paperSize.widthPoints.toFloat(),
        height = options.paperSize.heightPoints.toFloat(),
        workspace = workspace,
        quoteNumber = pagePlan.quoteNumber,
        quoteDate = pagePlan.quoteDate,
        validUntil = pagePlan.validUntil,
        pageNumber = pageNumber,
        pageCount = pagePlan.specs.size
    )

    private fun drawPage(context: PageContext, spec: PageSpec) {
        when (spec) {
            is PageSpec.Cover -> drawCover(context, spec)
            is PageSpec.SurfaceSummary -> drawSurfaceSummaryPage(context, spec)
            is PageSpec.Blueprint -> drawBlueprintPage(context, spec)
            is PageSpec.LineItems -> drawLineItemsPage(context, spec)
            PageSpec.Terms -> drawTermsPage(context)
        }
    }

    private fun drawCover(context: PageContext, spec: PageSpec.Cover) {
        val canvas = context.canvas
        val width = context.width
        val margin = MARGIN
        drawDocumentHeader(context)

        var y = 112f
        canvas.drawText(
            "CONTRACTOR ESTIMATE",
            margin,
            y,
            labelPaint(8.2f, ACCENT)
        )
        y += 28f
        canvas.drawText(
            fitText(
                context.workspace.project.name,
                titlePaint(25f),
                width - margin * 2f
            ),
            margin,
            y,
            titlePaint(25f)
        )
        y += 22f
        canvas.drawText(
            "Prepared for ${context.workspace.project.clientName.ifBlank { "client to be confirmed" }}",
            margin,
            y,
            bodyPaint(9.5f, MUTED_INK)
        )

        y += 22f
        canvas.drawLine(margin, y, width - margin, y, linePaint(0.9f, LINE))
        y += 18f

        drawHeroTotal(
            canvas = canvas,
            context = context,
            top = y,
            bottom = y + 100f
        )
        y += 118f

        val columnGap = 16f
        val columnWidth = (width - margin * 2f - columnGap) / 2f
        drawBreakdownPanel(
            canvas = canvas,
            quote = context.workspace.quote,
            left = margin,
            top = y,
            width = columnWidth,
            height = 124f
        )
        drawCoveragePanel(
            canvas = canvas,
            quote = context.workspace.quote,
            left = margin + columnWidth + columnGap,
            top = y,
            width = columnWidth,
            height = 124f
        )
        y += 146f

        val summaryTitle = if (spec.pageCount > 1) {
            "MEASURED SURFACES  ·  SUMMARY ${spec.pageIndex}/${spec.pageCount}"
        } else {
            "MEASURED SURFACES"
        }
        canvas.drawText(summaryTitle, margin, y, labelPaint(8.2f, INK))
        y += 13f
        drawSurfaceSummaryTable(
            canvas = canvas,
            surfaces = spec.surfaces,
            left = margin,
            top = y,
            width = width - margin * 2f,
            maxRows = SURFACES_PER_SUMMARY_PAGE
        )
    }

    private fun drawSurfaceSummaryPage(
        context: PageContext,
        spec: PageSpec.SurfaceSummary
    ) {
        val canvas = context.canvas
        drawDocumentHeader(context)
        val y = 112f
        canvas.drawText(
            "MEASURED SURFACES  ·  SUMMARY ${spec.pageIndex}/${spec.pageCount}",
            MARGIN,
            y,
            labelPaint(8.2f, INK)
        )
        canvas.drawText(
            "All measured surface totals reconcile to the project total on the cover page.",
            MARGIN,
            y + 16f,
            bodyPaint(8f, MUTED_INK)
        )
        drawSurfaceSummaryTable(
            canvas = canvas,
            surfaces = spec.surfaces,
            left = MARGIN,
            top = y + 30f,
            width = context.width - MARGIN * 2f,
            maxRows = SURFACES_PER_SUMMARY_PAGE
        )
    }

    private fun drawHeroTotal(
        canvas: Canvas,
        context: PageContext,
        top: Float,
        bottom: Float
    ) {
        val margin = MARGIN
        val width = context.width
        val rect = RectF(margin, top, width - margin, bottom)
        canvas.drawRect(rect, fillPaint(PANEL))
        canvas.drawRect(rect, linePaint(0.7f, LINE))
        canvas.drawRect(
            RectF(rect.left, rect.top, rect.left + 3f, rect.bottom),
            fillPaint(ACCENT)
        )

        val baseline = top + 20f
        canvas.drawText("CURRENT TOTAL", margin + 16f, baseline, labelPaint(7.8f, MUTED_INK))
        canvas.drawText(
            PdfQuoteFormat.money(context.workspace.quote.grandTotal),
            margin + 16f,
            top + 56f,
            moneyPaint(30f, INK)
        )
        canvas.drawText(
            "Includes materials, consumables, labor, and tax",
            margin + 16f,
            top + 76f,
            bodyPaint(7.8f, MUTED_INK)
        )

        val factWidth = 92f
        val factsLeft = width - margin - 16f - factWidth * 2f
        drawHeroFact(
            canvas,
            factsLeft,
            top + 20f,
            "NET AREA",
            String.format(
                Locale.US,
                "%.2f m2",
                context.workspace.quote.totalNetAreaSqMeters
            )
        )
        drawHeroFact(
            canvas,
            factsLeft + factWidth,
            top + 20f,
            "SURFACES",
            context.workspace.surfaces.size.toString()
        )
        drawHeroFact(
            canvas,
            factsLeft,
            top + 60f,
            "OPENINGS",
            context.workspace.surfaces.sumOf { it.cutouts.size }.toString()
        )
        drawHeroFact(
            canvas,
            factsLeft + factWidth,
            top + 60f,
            "VALID THROUGH",
            formatDate(context.validUntil)
        )
    }

    private fun drawHeroFact(
        canvas: Canvas,
        x: Float,
        top: Float,
        label: String,
        value: String
    ) {
        canvas.drawText(label, x, top + 8f, labelPaint(6.8f, MUTED_INK))
        canvas.drawText(
            fitText(value, numberPaint(8.4f, INK), 82f),
            x,
            top + 25f,
            numberPaint(8.4f, INK)
        )
    }

    private fun drawBreakdownPanel(
        canvas: Canvas,
        quote: ProjectQuoteSummary,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val rect = RectF(left, top, left + width, top + height)
        canvas.drawRect(rect, fillPaint(Color.WHITE))
        canvas.drawRect(rect, linePaint(0.7f, LINE))
        canvas.drawText("QUOTE BREAKDOWN", left + 12f, top + 17f, labelPaint(7.4f, INK))
        var y = top + 35f
        y = drawKeyValue(canvas, "Materials", PdfQuoteFormat.money(quote.materialSubtotal), left, y, width)
        y = drawKeyValue(canvas, "Consumables", PdfQuoteFormat.money(quote.consumablesSubtotal), left, y, width)
        y = drawKeyValue(canvas, "Labor", PdfQuoteFormat.money(quote.laborSubtotal), left, y, width)
        y = drawKeyValue(
            canvas,
            "Tax (${String.format(Locale.US, "%.1f%%", quote.taxRate * 100.0)})",
            PdfQuoteFormat.money(quote.taxAmount),
            left,
            y,
            width
        )
        canvas.drawLine(left + 12f, y + 1f, left + width - 12f, y + 1f, linePaint(0.6f, LINE))
        drawKeyValue(
            canvas,
            "Total",
            PdfQuoteFormat.money(quote.grandTotal),
            left,
            y + 7f,
            width,
            emphasized = true
        )
    }

    private fun drawCoveragePanel(
        canvas: Canvas,
        quote: ProjectQuoteSummary,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val rect = RectF(left, top, left + width, top + height)
        canvas.drawRect(rect, fillPaint(Color.WHITE))
        canvas.drawRect(rect, linePaint(0.7f, LINE))
        canvas.drawText("MEASUREMENT BASIS", left + 12f, top + 17f, labelPaint(7.4f, INK))
        var y = top + 35f
        y = drawKeyValue(
            canvas,
            "Gross area",
            String.format(Locale.US, "%.2f m2", quote.totalGrossAreaSqMeters),
            left,
            y,
            width
        )
        y = drawKeyValue(
            canvas,
            "Opening deductions",
            String.format(Locale.US, "-%.2f m2", quote.totalCutoutAreaSqMeters),
            left,
            y,
            width
        )
        y = drawKeyValue(
            canvas,
            "Net priced area",
            String.format(Locale.US, "%.2f m2", quote.totalNetAreaSqMeters),
            left,
            y,
            width
        )
        y = drawKeyValue(
            canvas,
            "Imperial area",
            String.format(Locale.US, "%.2f sq ft", UnitConverter.sqMetersToSqFeet(quote.totalNetAreaSqMeters)),
            left,
            y,
            width
        )
        drawKeyValue(
            canvas,
            "Tax basis",
            if (quote.taxPolicy == TaxPolicy.TAX_TOTAL_SUBTOTAL) "Goods + labor" else "Goods only",
            left,
            y,
            width
        )
    }

    private fun drawSurfaceSummaryTable(
        canvas: Canvas,
        surfaces: List<SurfaceQuoteLine>,
        left: Float,
        top: Float,
        width: Float,
        maxRows: Int
    ) {
        val nameWidth = width * 0.34f
        val materialWidth = width * 0.32f
        val areaWidth = width * 0.16f
        val amountWidth = width - nameWidth - materialWidth - areaWidth
        val headerBaseline = top + 11f
        canvas.drawText("SURFACE", left, headerBaseline, labelPaint(6.8f, MUTED_INK))
        canvas.drawText("MATERIAL", left + nameWidth, headerBaseline, labelPaint(6.8f, MUTED_INK))
        drawRightAlignedText(
            canvas,
            "NET AREA",
            left + nameWidth + materialWidth + areaWidth,
            headerBaseline,
            labelPaint(6.8f, MUTED_INK)
        )
        drawRightAlignedText(
            canvas,
            "TOTAL",
            left + width,
            headerBaseline,
            labelPaint(6.8f, MUTED_INK)
        )
        var y = top + 15f
        canvas.drawLine(left, y, left + width, y, linePaint(0.65f, LINE))

        surfaces.take(maxRows).forEach { surface ->
            y += 21f
            canvas.drawText(
                fitText(surface.surfaceName, bodyPaint(7.7f, INK), nameWidth - 8f),
                left,
                y,
                bodyPaint(7.7f, INK)
            )
            canvas.drawText(
                fitText(surface.effectiveMaterial.name, bodyPaint(7.2f, MUTED_INK), materialWidth - 8f),
                left + nameWidth,
                y,
                bodyPaint(7.2f, MUTED_INK)
            )
            drawRightAlignedText(
                canvas,
                String.format(Locale.US, "%.2f m2", surface.analysis?.netAreaSqMeters ?: 0.0),
                left + nameWidth + materialWidth + areaWidth,
                y,
                numberPaint(7.4f, INK)
            )
            drawRightAlignedText(
                canvas,
                surface.estimate?.grandTotal?.let(PdfQuoteFormat::money) ?: "-",
                left + width,
                y,
                numberPaint(7.4f, INK)
            )
            canvas.drawLine(left, y + 5f, left + width, y + 5f, linePaint(0.45f, RULE))
        }

        check(surfaces.size <= maxRows) {
            "Surface summary pages must not truncate more than $maxRows rows."
        }
    }

    private fun drawBlueprintPage(
        context: PageContext,
        page: PageSpec.Blueprint
    ) {
        val canvas = context.canvas
        val margin = MARGIN
        drawDocumentHeader(context)
        canvas.drawText(
            "SURFACE BLUEPRINT ${page.index} OF ${page.total}",
            margin,
            106f,
            labelPaint(7.8f, ACCENT)
        )
        canvas.drawText(
            fitText(page.surface.surfaceName, titlePaint(18f), context.width - margin * 2f),
            margin,
            130f,
            titlePaint(18f)
        )
        canvas.drawText(
            fitText(
                "${page.surface.effectiveMaterial.tradeType.displayName} | " +
                    page.surface.effectiveMaterial.name,
                bodyPaint(8.2f, MUTED_INK),
                context.width - margin * 2f
            ),
            margin,
            146f,
            bodyPaint(8.2f, MUTED_INK)
        )

        val drawingBounds = RectF(
            margin,
            160f,
            context.width - margin,
            context.height - 106f
        )
        blueprintRenderer.render(
            canvas = canvas,
            bounds = drawingBounds,
            surface = page.surface
        )

        val summaryTop = drawingBounds.bottom + 15f
        val estimate = page.surface.estimate
        drawBlueprintFact(canvas, margin, summaryTop, "SURFACE TOTAL", estimate?.grandTotal?.let(PdfQuoteFormat::money) ?: "-")
        drawBlueprintFact(
            canvas,
            margin + 100f,
            summaryTop,
            "MATERIAL",
            estimate?.materialSubtotal?.let(PdfQuoteFormat::money) ?: "-"
        )
        drawBlueprintFact(
            canvas,
            margin + 200f,
            summaryTop,
            "LABOR",
            estimate?.laborSubtotal?.let(PdfQuoteFormat::money) ?: "-"
        )
        drawBlueprintFact(
            canvas,
            margin + 300f,
            summaryTop,
            "PACKAGES",
            estimate?.primaryUnitsRequired?.toString() ?: "-"
        )
        drawBlueprintFact(
            canvas,
            margin + 400f,
            summaryTop,
            "WASTE",
            page.surface.effectiveMaterial.effectiveWasteFactor.let {
                String.format(Locale.US, "%.1f%%", it * 100.0)
            }
        )

        val cutoutText = if (page.surface.cutouts.isEmpty()) {
            "No openings deducted."
        } else {
            page.surface.cutouts.joinToString(" | ") { cutout ->
                "${cutout.kind.name.replace('_', ' ')} ${cutout.label}"
            }
        }
        canvas.drawText(
            fitText(cutoutText, bodyPaint(7.2f, MUTED_INK), context.width - margin * 2f),
            margin,
            summaryTop + 28f,
            bodyPaint(7.2f, MUTED_INK)
        )
    }

    private fun drawBlueprintFact(
        canvas: Canvas,
        x: Float,
        top: Float,
        label: String,
        value: String
    ) {
        canvas.drawText(label, x, top + 7f, labelPaint(6.5f, MUTED_INK))
        canvas.drawText(
            fitText(value, numberPaint(8.2f, INK), 102f),
            x,
            top + 21f,
            numberPaint(8.2f, INK)
        )
    }

    private fun drawLineItemsPage(
        context: PageContext,
        page: PageSpec.LineItems
    ) {
        val canvas = context.canvas
        val margin = MARGIN
        drawDocumentHeader(context)
        canvas.drawText("ITEMIZED QUOTE", margin, 106f, labelPaint(7.8f, ACCENT))
        canvas.drawText("Material, consumable, and labor lines", margin, 128f, titlePaint(18f))
        canvas.drawText(
            "Quantities follow measured net area and package rounding.",
            margin,
            144f,
            bodyPaint(8f, MUTED_INK)
        )

        val contentWidth = context.width - margin * 2f
        val surfaceWidth = 78f
        val quantityWidth = 48f
        val unitPriceWidth = 66f
        val totalWidth = 70f
        val descriptionWidth = contentWidth - surfaceWidth - quantityWidth - unitPriceWidth - totalWidth
        val descriptionX = margin + surfaceWidth
        val quantityX = descriptionX + descriptionWidth
        val unitPriceX = quantityX + quantityWidth

        var y = 170f
        canvas.drawText("SURFACE", margin, y, labelPaint(6.7f, MUTED_INK))
        canvas.drawText("LINE ITEM", descriptionX, y, labelPaint(6.7f, MUTED_INK))
        drawRightAlignedText(canvas, "QTY", quantityX + quantityWidth - 4f, y, labelPaint(6.7f, MUTED_INK))
        drawRightAlignedText(canvas, "UNIT PRICE", unitPriceX + unitPriceWidth - 4f, y, labelPaint(6.7f, MUTED_INK))
        drawRightAlignedText(canvas, "LINE TOTAL", margin + contentWidth, y, labelPaint(6.7f, MUTED_INK))
        y += 8f
        canvas.drawLine(margin, y, margin + contentWidth, y, linePaint(0.8f, INK))
        y += 9f

        page.rows.forEach { row ->
            drawTableRow(
                canvas = canvas,
                row = row,
                margin = margin,
                y = y,
                surfaceWidth = surfaceWidth,
                descriptionX = descriptionX,
                descriptionWidth = descriptionWidth,
                quantityRight = quantityX + quantityWidth - 4f,
                unitPriceRight = unitPriceX + unitPriceWidth - 4f,
                totalRight = margin + contentWidth
            )
            y += ROW_HEIGHT
            canvas.drawLine(margin, y, margin + contentWidth, y, linePaint(0.45f, RULE))
        }

        if (page.rows.isEmpty()) {
            canvas.drawText(
                "No itemized quote lines are available for this project.",
                margin,
                y + 20f,
                bodyPaint(8.5f, MUTED_INK)
            )
        }

        if (page.showTotals) {
            drawLineItemTotals(
                canvas = canvas,
                quote = context.workspace.quote,
                left = margin,
                top = y + 16f,
                width = contentWidth
            )
        }
    }

    private fun drawTableRow(
        canvas: Canvas,
        row: QuoteTableRow,
        margin: Float,
        y: Float,
        surfaceWidth: Float,
        descriptionX: Float,
        descriptionWidth: Float,
        quantityRight: Float,
        unitPriceRight: Float,
        totalRight: Float
    ) {
        canvas.drawText(
            fitText(row.surfaceName, bodyPaint(7.1f, MUTED_INK), surfaceWidth - 6f),
            margin,
            y + 10f,
            bodyPaint(7.1f, MUTED_INK)
        )
        val description = "${PdfQuoteFormat.categoryLabel(row.item.category)} - " +
            "${row.item.description}\n${row.item.skuOrBasis}"
        drawStaticText(
            canvas = canvas,
            text = description,
            paint = bodyPaint(7.3f, INK),
            x = descriptionX,
            y = y + 4f,
            width = descriptionWidth - 8f,
            maxLines = 2
        )
        drawRightAlignedText(
            canvas,
            PdfQuoteFormat.quantity(row.item.quantity),
            quantityRight,
            y + 10f,
            numberPaint(7.1f, INK)
        )
        drawRightAlignedText(
            canvas,
            row.item.unitLabel,
            quantityRight,
            y + 21f,
            bodyPaint(6.2f, MUTED_INK)
        )
        drawRightAlignedText(
            canvas,
            PdfQuoteFormat.money(row.item.unitPrice),
            unitPriceRight,
            y + 10f,
            numberPaint(7.1f, INK)
        )
        drawRightAlignedText(
            canvas,
            PdfQuoteFormat.money(row.item.lineTotal),
            totalRight,
            y + 10f,
            numberPaint(7.1f, INK)
        )
    }

    private fun drawLineItemTotals(
        canvas: Canvas,
        quote: ProjectQuoteSummary,
        left: Float,
        top: Float,
        width: Float
    ) {
        val blockLeft = left + width * 0.52f
        var y = top
        canvas.drawLine(blockLeft, y, left + width, y, linePaint(0.9f, INK))
        y += 14f
        y = drawKeyValue(canvas, "Materials", PdfQuoteFormat.money(quote.materialSubtotal), blockLeft, y, width * 0.48f)
        y = drawKeyValue(canvas, "Consumables", PdfQuoteFormat.money(quote.consumablesSubtotal), blockLeft, y, width * 0.48f)
        y = drawKeyValue(canvas, "Labor", PdfQuoteFormat.money(quote.laborSubtotal), blockLeft, y, width * 0.48f)
        y = drawKeyValue(
            canvas,
            "Tax (${String.format(Locale.US, "%.1f%%", quote.taxRate * 100.0)})",
            PdfQuoteFormat.money(quote.taxAmount),
            blockLeft,
            y,
            width * 0.48f
        )
        canvas.drawLine(blockLeft, y + 2f, left + width, y + 2f, linePaint(0.7f, LINE))
        drawKeyValue(
            canvas,
            "QUOTED TOTAL",
            PdfQuoteFormat.money(quote.grandTotal),
            blockLeft,
            y + 9f,
            width * 0.48f,
            emphasized = true
        )
    }

    private fun drawTermsPage(context: PageContext) {
        val canvas = context.canvas
        val margin = MARGIN
        drawDocumentHeader(context)
        canvas.drawText("TERMS & ACCEPTANCE", margin, 108f, labelPaint(7.8f, ACCENT))
        canvas.drawText("Quote terms", margin, 132f, titlePaint(20f))

        var y = 158f
        val width = context.width - margin * 2f
        val terms = listOf(
            "This estimate is valid through ${formatDate(context.validUntil)}. " +
                "Pricing may be revised after that date.",
            "Quantities are calculated from measured net surface area, opening deductions, " +
                "material package coverage, waste allowance, labor rate, and project tax settings.",
            "Existing site conditions, substrate preparation, access, and code requirements " +
                "may require a written change order before work begins.",
            "Work is scheduled after written acceptance and the deposit agreed with the contractor.",
            "Any unused material remains the contractor's property unless the accepted scope states otherwise."
        )
        terms.forEachIndexed { index, term ->
            y = drawStaticText(
                canvas = canvas,
                text = "${index + 1}. $term",
                paint = bodyPaint(9f, INK),
                x = margin,
                y = y,
                width = width,
                maxLines = 4,
                lineSpacing = 1.18f
            ) + 10f
        }

        val acceptanceTop = context.height - 202f
        val box = RectF(margin, acceptanceTop, context.width - margin, context.height - 66f)
        canvas.drawRect(box, fillPaint(PANEL))
        canvas.drawRect(box, linePaint(0.8f, LINE))
        canvas.drawText("CLIENT ACCEPTANCE", margin + 14f, acceptanceTop + 20f, labelPaint(7.5f, INK))
        canvas.drawText(
            "Quoted total ${PdfQuoteFormat.money(context.workspace.quote.grandTotal)}",
            margin + 14f,
            acceptanceTop + 39f,
            numberPaint(10f, INK)
        )

        val lineY = acceptanceTop + 80f
        val signatureWidth = (width - 50f) / 2f
        canvas.drawLine(margin + 14f, lineY, margin + 14f + signatureWidth, lineY, linePaint(0.8f, INK))
        canvas.drawLine(
            margin + 32f + signatureWidth,
            lineY,
            context.width - margin - 14f,
            lineY,
            linePaint(0.8f, INK)
        )
        canvas.drawText(
            "Client signature",
            margin + 14f,
            lineY + 13f,
            bodyPaint(7.3f, MUTED_INK)
        )
        canvas.drawText(
            "Date",
            margin + 32f + signatureWidth,
            lineY + 13f,
            bodyPaint(7.3f, MUTED_INK)
        )
        canvas.drawText(
            "Prepared by ${context.workspace.project.contractorName.ifBlank { "the contractor" }}",
            margin + 14f,
            acceptanceTop + 110f,
            bodyPaint(7.2f, MUTED_INK)
        )
    }

    private fun drawDocumentHeader(context: PageContext) {
        val canvas = context.canvas
        val margin = MARGIN
        val project = context.workspace.project
        val contractorName = project.contractorName.ifBlank { "Independent contractor" }
        val contractorDetails = listOf(project.contractorContact, project.contractorLicense)
            .filter(String::isNotBlank)
            .joinToString("  |  ")
            .ifBlank { "AR CONTRACTOR ESTIMATE" }
        val brandTextWidth = context.width - margin * 2f - 150f
        drawBrandMark(canvas, margin, 30f, 25f)
        canvas.drawText(
            fitText(contractorName, labelPaint(8.4f, INK), brandTextWidth),
            margin + 34f,
            42f,
            labelPaint(8.4f, INK)
        )
        canvas.drawText(
            fitText(contractorDetails, bodyPaint(6.4f, MUTED_INK), brandTextWidth),
            margin + 34f,
            54f,
            bodyPaint(6.4f, MUTED_INK)
        )
        drawRightAlignedText(
            canvas,
            context.quoteNumber,
            context.width - margin,
            42f,
            numberPaint(7.6f, INK)
        )
        drawRightAlignedText(
            canvas,
            formatDate(context.quoteDate),
            context.width - margin,
            54f,
            bodyPaint(7f, MUTED_INK)
        )
        canvas.drawLine(margin, 72f, context.width - margin, 72f, linePaint(0.8f, LINE))
    }

    private fun drawFooter(context: PageContext) {
        val canvas = context.canvas
        val margin = MARGIN
        val y = context.height - 30f
        canvas.drawLine(margin, y, context.width - margin, y, linePaint(0.55f, LINE))
        canvas.drawText(
            "${context.workspace.project.name} | ${context.quoteNumber}",
            margin,
            y + 11f,
            bodyPaint(6.4f, MUTED_INK)
        )
        drawRightAlignedText(
            canvas,
            "Page ${context.pageNumber} of ${context.pageCount}",
            context.width - margin,
            y + 11f,
            numberPaint(6.4f, MUTED_INK)
        )
    }

    private fun drawBrandMark(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float
    ) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y + size)
            lineTo(x + size, y + size)
        }
        canvas.drawPath(path, linePaint(2f, INK))
        canvas.drawLine(x, y, x + size * 0.55f, y, linePaint(2f, INK))
        canvas.drawCircle(x + size * 0.80f, y + size * 0.20f, 2.5f, fillPaint(ACCENT))
    }

    private fun drawKeyValue(
        canvas: Canvas,
        label: String,
        value: String,
        left: Float,
        baseline: Float,
        width: Float,
        emphasized: Boolean = false
    ): Float {
        val paint = if (emphasized) numberPaint(8.4f, INK) else bodyPaint(7.5f, MUTED_INK)
        canvas.drawText(label, left + 12f, baseline, paint)
        drawRightAlignedText(
            canvas,
            value,
            left + width - 12f,
            baseline,
            if (emphasized) numberPaint(8.4f, INK) else numberPaint(7.5f, INK)
        )
        return baseline + 17f
    }

    private fun drawStaticText(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        y: Float,
        width: Float,
        maxLines: Int,
        lineSpacing: Float = 1.05f
    ): Float {
        val textPaint = TextPaint(paint)
        val layout = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            textPaint,
            width.toInt().coerceAtLeast(1)
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, lineSpacing)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.withTranslation(x, y) {
            layout.draw(this)
        }
        return y + layout.height
    }

    private fun drawRightAlignedText(
        canvas: Canvas,
        text: String,
        right: Float,
        baseline: Float,
        paint: Paint
    ) {
        val originalAlign = paint.textAlign
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(text, right, baseline, paint)
        paint.textAlign = originalAlign
    }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "..."
        val available = maxWidth - paint.measureText(ellipsis)
        if (available <= 0f) return ellipsis
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (paint.measureText(text, 0, mid) <= available) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return text.take(low).trimEnd() + ellipsis
    }

    private fun buildTableRows(workspace: ProjectWorkspace): List<QuoteTableRow> =
        workspace.surfaces.flatMap { surface ->
            surface.estimate?.lineItems.orEmpty().map { item ->
                QuoteTableRow(
                    surfaceName = surface.surfaceName,
                    item = item
                )
            }
        }

    private fun labelPaint(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = SANS_MEDIUM
    }

    private fun bodyPaint(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = SANS
    }

    private fun numberPaint(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = MONO
    }

    private fun moneyPaint(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = MONO_BOLD
    }

    private fun titlePaint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        textSize = size
        typeface = SERIF_BOLD
    }

    private fun linePaint(width: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun formatDate(date: LocalDate): String =
        date.format(DATE_FORMATTER)

    private data class QuoteTableRow(
        val surfaceName: String,
        val item: QuoteLineItem
    )

    private data class PageContext(
        val canvas: Canvas,
        val width: Float,
        val height: Float,
        val workspace: ProjectWorkspace,
        val quoteNumber: String,
        val quoteDate: LocalDate,
        val validUntil: LocalDate,
        val pageNumber: Int,
        val pageCount: Int
    )

    private data class PagePlan(
        val quoteDate: LocalDate,
        val validUntil: LocalDate,
        val quoteNumber: String,
        val specs: List<PageSpec>
    )

    private sealed interface PageSpec {
        data class Cover(
            val surfaces: List<SurfaceQuoteLine>,
            val pageIndex: Int,
            val pageCount: Int
        ) : PageSpec

        data class SurfaceSummary(
            val surfaces: List<SurfaceQuoteLine>,
            val pageIndex: Int,
            val pageCount: Int
        ) : PageSpec
        data class Blueprint(
            val surface: SurfaceQuoteLine,
            val index: Int,
            val total: Int
        ) : PageSpec

        data class LineItems(
            val rows: List<QuoteTableRow>,
            val showTotals: Boolean
        ) : PageSpec

        data object Terms : PageSpec
    }

    private companion object {
        const val MARGIN = 40f
        const val ROW_HEIGHT = 35f
        const val ROWS_PER_TABLE_PAGE = 12
        const val SURFACES_PER_SUMMARY_PAGE = 8

        val INK: Int = Color.rgb(24, 27, 31)
        val MUTED_INK: Int = Color.rgb(88, 97, 109)
        val LINE: Int = Color.rgb(210, 217, 225)
        val RULE: Int = Color.rgb(234, 237, 241)
        val PANEL: Int = Color.rgb(247, 248, 250)
        val ACCENT: Int = Color.rgb(29, 95, 191)

        val SANS: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val SANS_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val MONO: Typeface = Typeface.create("monospace", Typeface.NORMAL)
        val MONO_BOLD: Typeface = Typeface.create("monospace", Typeface.BOLD)
        val SERIF_BOLD: Typeface = Typeface.create("serif", Typeface.BOLD)
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    }
}
