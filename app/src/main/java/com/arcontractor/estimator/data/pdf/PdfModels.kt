package com.arcontractor.estimator.data.pdf

import android.net.Uri
import com.arcontractor.estimator.data.repository.ProjectWorkspace
import java.io.File

enum class PdfPaperSize(
    val displayName: String,
    val widthPoints: Int,
    val heightPoints: Int
) {
    A4("A4 portrait", 595, 842),
    LETTER("US Letter portrait", 612, 792)
}

data class PdfExportOptions(
    val paperSize: PdfPaperSize = PdfPaperSize.A4,
    val validityDays: Int = 30
) {
    init {
        require(validityDays in 1..365) { "Quote validity must be between 1 and 365 days." }
    }
}

data class PdfExportResult(
    val file: File,
    val contentUri: Uri,
    val pageCount: Int
)

interface QuotePdfExporter {
    suspend fun export(
        workspace: ProjectWorkspace,
        options: PdfExportOptions
    ): PdfExportResult
}
