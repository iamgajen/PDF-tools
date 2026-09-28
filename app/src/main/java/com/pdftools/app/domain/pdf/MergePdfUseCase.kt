package com.pdftools.app.domain.pdf

import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

class MergePdfUseCase(
    private val pdfRepository: PdfRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend operator fun invoke(inputUris: List<Uri>, outputStream: OutputStream): Result<Int> = withContext(ioDispatcher) {
        if (inputUris.size < 2) {
            return@withContext Result.failure(IllegalArgumentException("Please select at least two PDF files to merge."))
        }
        pdfRepository.mergePdfs(inputUris, outputStream)
    }

    suspend fun executeToTempFile(inputUris: List<Uri>): Result<File> = withContext(ioDispatcher) {
        if (inputUris.size < 2) {
            return@withContext Result.failure(IllegalArgumentException("Please select at least two PDF files to merge."))
        }
        pdfRepository.createTempMergedFile(inputUris)
    }
}
