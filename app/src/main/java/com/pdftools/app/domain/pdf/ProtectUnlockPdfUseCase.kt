package com.pdftools.app.domain.pdf

import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProtectUnlockPdfUseCase(
    private val pdfRepository: PdfRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun protectPdf(params: ProtectPdfParams): Result<ProtectUnlockOutputFile> = withContext(ioDispatcher) {
        if (params.password.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Password cannot be empty."))
        }

        pdfRepository.protectPdf(params)
    }

    suspend fun unlockPdf(params: UnlockPdfParams): Result<ProtectUnlockOutputFile> = withContext(ioDispatcher) {
        if (params.password.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Password cannot be empty."))
        }

        pdfRepository.unlockPdf(params)
    }
}
