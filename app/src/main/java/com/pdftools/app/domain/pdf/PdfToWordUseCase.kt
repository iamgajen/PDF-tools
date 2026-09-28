package com.pdftools.app.domain.pdf

import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository

class PdfToWordUseCase(
    private val pdfRepository: PdfRepository
) {
    suspend operator fun invoke(
        inputUri: Uri,
        baseFileName: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<PdfToWordResult> {
        return pdfRepository.convertPdfToWord(inputUri, baseFileName, onProgress)
    }
}
