package com.pdftools.app.domain.pdf

import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SplitPdfUseCase(
    private val pdfRepository: PdfRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend operator fun invoke(
        inputUri: Uri,
        baseFileName: String,
        splitMode: SplitMode,
        pageCount: Int
    ): Result<List<SplitOutputFile>> = withContext(ioDispatcher) {
        val pageGroups = when (splitMode) {
            is SplitMode.EveryPage -> {
                if (pageCount <= 0) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid document page count."))
                }
                (0 until pageCount).map { listOf(it) }
            }
            is SplitMode.ByRanges -> {
                when (val parseResult = PageRangeParser.parseRanges(splitMode.rangeString, pageCount)) {
                    is PageRangeParseResult.Success -> parseResult.pageGroups
                    is PageRangeParseResult.Error -> return@withContext Result.failure(IllegalArgumentException(parseResult.message))
                }
            }
            is SplitMode.ExtractPages -> {
                when (val parseResult = PageRangeParser.parseExtractPages(splitMode.pagesString, pageCount)) {
                    is PageRangeParseResult.Success -> parseResult.pageGroups
                    is PageRangeParseResult.Error -> return@withContext Result.failure(IllegalArgumentException(parseResult.message))
                }
            }
        }

        if (pageGroups.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No page selection provided for splitting."))
        }

        pdfRepository.splitPdf(
            inputUri = inputUri,
            baseFileName = baseFileName,
            splitMode = splitMode,
            pageGroups = pageGroups
        )
    }

    suspend fun invokeWithPageGroups(
        inputUri: Uri,
        baseFileName: String,
        splitMode: SplitMode,
        pageGroups: List<List<Int>>
    ): Result<List<SplitOutputFile>> = withContext(ioDispatcher) {
        if (pageGroups.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No page selection provided for splitting."))
        }

        pdfRepository.splitPdf(
            inputUri = inputUri,
            baseFileName = baseFileName,
            splitMode = splitMode,
            pageGroups = pageGroups
        )
    }
}
