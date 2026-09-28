package com.pdftools.app.domain.pdf

import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompressPdfUseCase(
    private val pdfRepository: PdfRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend operator fun invoke(
        inputUri: Uri,
        baseFileName: String,
        level: CompressionLevel
    ): Result<CompressPdfResult> = withContext(ioDispatcher) {
        pdfRepository.compressPdf(
            inputUri = inputUri,
            baseFileName = baseFileName,
            level = level
        )
    }
}
