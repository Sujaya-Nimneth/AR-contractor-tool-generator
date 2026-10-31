package com.arcontractor.estimator.data.pdf

import android.content.Context
import androidx.core.content.FileProvider
import com.arcontractor.estimator.data.repository.ProjectWorkspace
import java.io.File
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes quote PDFs into app-private storage and exposes them through a read-only FileProvider URI.
 */
class AndroidQuotePdfExporter(
    context: Context,
    private val generator: PdfQuoteGenerator = PdfQuoteGenerator(),
    private val clock: Clock = Clock.systemDefaultZone()
) : QuotePdfExporter {
    private val appContext = context.applicationContext
    private val authority = "${appContext.packageName}.fileprovider"

    override suspend fun export(
        workspace: ProjectWorkspace,
        options: PdfExportOptions
    ): PdfExportResult = withContext(Dispatchers.IO) {
        val exportDirectory = File(appContext.filesDir, EXPORT_DIRECTORY)
        check(exportDirectory.exists() || exportDirectory.mkdirs()) {
            "The quote export directory could not be created."
        }

        val fileName = PdfQuoteFormat.fileName(
            projectName = workspace.project.name,
            quoteDate = LocalDate.now(clock)
        )
        val outputFile = File(exportDirectory, fileName)
        val pageCount = generator.generate(
            workspace = workspace,
            options = options,
            outputFile = outputFile
        )
        val contentUri = FileProvider.getUriForFile(
            appContext,
            authority,
            outputFile
        )
        PdfExportResult(
            file = outputFile,
            contentUri = contentUri,
            pageCount = pageCount
        )
    }

    private companion object {
        const val EXPORT_DIRECTORY = "quote-exports"
    }
}
