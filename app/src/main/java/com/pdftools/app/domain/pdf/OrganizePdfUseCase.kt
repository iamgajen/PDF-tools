package com.pdftools.app.domain.pdf

import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OrganizePdfUseCase(
    private val pdfRepository: PdfRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend operator fun invoke(
        inputUri: Uri,
        baseFileName: String,
        pageSpecs: List<OrganizePageSpec>
    ): Result<OrganizeOutputFile> = withContext(ioDispatcher) {
        if (pageSpecs.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please select at least one page to keep."))
        }
        pdfRepository.organizePdf(
            inputUri = inputUri,
            baseFileName = baseFileName,
            pageSpecs = pageSpecs
        )
    }
}
