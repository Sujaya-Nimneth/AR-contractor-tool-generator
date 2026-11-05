package com.arcontractor.estimator.data.pdf

import com.arcontractor.estimator.domain.estimation.QuoteLineCategory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfQuoteFormat {
    fun quoteNumber(projectId: String, quoteDate: LocalDate): String {
        val suffix = projectId.hashCode()
            .toUInt()
            .toString(16)
            .uppercase(Locale.US)
            .padStart(6, '0')
            .take(6)
        return "Q-${quoteDate.format(DateTimeFormatter.BASIC_ISO_DATE)}-$suffix"
    }

    fun fileName(
        projectName: String,
        quoteDate: LocalDate,
        exportTime: LocalDateTime = quoteDate.atStartOfDay()
    ): String {
        val slug = projectName
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "contractor-quote" }
            .take(48)
        val revision = exportTime.format(DateTimeFormatter.ofPattern("HHmmss", Locale.US))
        return "quote-$slug-${quoteDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}-$revision.pdf"
    }

    fun money(value: BigDecimal): String =
        String.format(Locale.US, "$%,.2f", value)

    fun quantity(value: Double): String =
        if (kotlin.math.abs(value - value.toLong()) < 1e-9) {
            String.format(Locale.US, "%,d", value.toLong())
        } else {
            String.format(Locale.US, "%.2f", value)
        }

    fun categoryLabel(category: QuoteLineCategory): String = when (category) {
        QuoteLineCategory.PRIMARY_MATERIAL -> "Material"
        QuoteLineCategory.CONSUMABLE -> "Consumable"
        QuoteLineCategory.LABOR -> "Labor"
    }

    fun <T> chunkForPages(rows: List<T>, pageSize: Int): List<List<T>> {
        require(pageSize > 0) { "PDF page size must be positive." }
        if (rows.isEmpty()) return listOf(emptyList())
        return rows.chunked(pageSize)
    }
}
