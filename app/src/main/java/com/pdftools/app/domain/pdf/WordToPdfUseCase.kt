package com.pdftools.app.domain.pdf

import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository

class WordToPdfUseCase(
    private val pdfRepository: PdfRepository
) {
    suspend operator fun invoke(
        inputUri: Uri,
        baseFileName: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<WordToPdfResult> {
        return pdfRepository.convertWordToPdf(inputUri, baseFileName, onProgress)
    }
}
