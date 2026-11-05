package com.arcontractor.estimator.data.pdf

import com.arcontractor.estimator.domain.estimation.QuoteLineCategory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfQuoteFormatTest {
    @Test
    fun `quote number and file name are deterministic and filesystem safe`() {
        val date = LocalDate.of(2026, 10, 31)
        val first = PdfQuoteFormat.quoteNumber("default-project", date)
        val second = PdfQuoteFormat.quoteNumber("default-project", date)

        assertEquals(first, second)
        assertTrue(first.matches(Regex("Q-\\d{8}-[0-9A-F]{6}")))
        assertEquals(
            "quote-harbor-street-renovation-2026-10-31-000000.pdf",
            PdfQuoteFormat.fileName("Harbor Street / Renovation", date)
        )
        assertEquals(
            "quote-contractor-quote-2026-10-31-000000.pdf",
            PdfQuoteFormat.fileName("---", date)
        )
        val later = LocalDateTime.of(2026, 10, 31, 14, 32, 9)
        assertTrue(
            PdfQuoteFormat.fileName("Harbor Street / Renovation", date, later)
                .endsWith("-143209.pdf")
        )
    }

    @Test
    fun `financial and quantity formatting is stable`() {
        assertEquals("$1,234.50", PdfQuoteFormat.money(BigDecimal("1234.50")))
        assertEquals("12", PdfQuoteFormat.quantity(12.0))
        assertEquals("12.25", PdfQuoteFormat.quantity(12.25))
        assertEquals("Consumable", PdfQuoteFormat.categoryLabel(QuoteLineCategory.CONSUMABLE))
    }

    @Test
    fun `line item pagination preserves order and produces an empty page when needed`() {
        val rows = (1..25).toList()

        assertEquals(listOf(12, 12, 1), PdfQuoteFormat.chunkForPages(rows, 12).map(List<Int>::size))
        assertEquals(1, PdfQuoteFormat.chunkForPages(rows, 12).first().first())
        assertEquals(25, PdfQuoteFormat.chunkForPages(rows, 12).last().last())
        val emptyPages = PdfQuoteFormat.chunkForPages(emptyList<Int>(), 12)
        assertEquals(1, emptyPages.size)
        assertTrue(emptyPages.single().isEmpty())
    }
}
