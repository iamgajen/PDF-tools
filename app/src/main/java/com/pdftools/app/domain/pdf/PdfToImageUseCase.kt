package com.pdftools.app.domain.pdf

import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfToImageUseCase(
    private val pdfRepository: PdfRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend operator fun invoke(
        params: PdfToImageParams,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<PdfToImageResult> = withContext(ioDispatcher) {
        if (params.pagesToRender.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please select at least one page to convert."))
        }

        pdfRepository.convertPdfToImages(
            params = params,
            onProgress = onProgress
        )
    }
}
