package com.arcontractor.estimator.data.pdf

import android.content.Context
import androidx.core.content.FileProvider
import com.arcontractor.estimator.data.repository.ProjectWorkspace
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
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

        val now = LocalDateTime.now(clock)
        val fileName = PdfQuoteFormat.fileName(
            projectName = workspace.project.name,
            quoteDate = now.toLocalDate(),
            exportTime = now
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
        pruneOldExports(exportDirectory, keepFile = outputFile)
        PdfExportResult(
            file = outputFile,
            contentUri = contentUri,
            pageCount = pageCount
        )
    }

    private fun pruneOldExports(directory: File, keepFile: File) {
        val exports = directory.listFiles { file ->
            file.isFile && file.name.endsWith(".pdf")
        }?.sortedByDescending(File::lastModified).orEmpty()
        exports.drop(MAX_PRIVATE_EXPORTS).forEach { file ->
            if (file.absolutePath != keepFile.absolutePath) {
                file.delete()
            }
        }
    }

    private companion object {
        const val EXPORT_DIRECTORY = "quote-exports"
        const val MAX_PRIVATE_EXPORTS = 12
    }
}
