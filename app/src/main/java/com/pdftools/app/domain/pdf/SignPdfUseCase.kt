package com.pdftools.app.domain.pdf

import com.pdftools.app.data.pdf.PdfRepository

class SignPdfUseCase(
    private val pdfRepository: PdfRepository
) {
    suspend operator fun invoke(params: SignPdfParams): Result<SignOutputFile> {
        if (params.signatures.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please add at least one signature to sign the PDF."))
        }
        return pdfRepository.signPdf(params)
    }
}
