package com.pdftools.app.domain.pdf

import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageToPdfUseCase(
    private val pdfRepository: PdfRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend operator fun invoke(
        images: List<ImageSpec>,
        baseFileName: String,
        pageSize: ImageToPdfPageSize = ImageToPdfPageSize.A4,
        quality: ImageToPdfQuality = ImageToPdfQuality.HIGH,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<ImageToPdfResult> = withContext(ioDispatcher) {
        if (images.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please select at least one image to create a PDF."))
        }

        val cleanName = if (baseFileName.isNotBlank()) {
            val trimmed = baseFileName.trim()
            if (trimmed.endsWith(".pdf", ignoreCase = true)) {
                trimmed
            } else {
                "$trimmed.pdf"
            }
        } else {
            "images.pdf"
        }

        pdfRepository.convertImagesToPdf(
            images = images,
            baseFileName = cleanName,
            pageSize = pageSize,
            quality = quality,
            onProgress = onProgress
        )
    }
}
