package com.pdftools.app.domain.pdf

import android.net.TestUri
import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream

class SignPdfUseCaseTest {

    private lateinit var fakeRepository: FakePdfRepository
    private lateinit var signPdfUseCase: SignPdfUseCase

    @Before
    fun setUp() {
        fakeRepository = FakePdfRepository()
        signPdfUseCase = SignPdfUseCase(fakeRepository)
    }

    @Test
    fun testSignPdfFailsWhenSignaturesEmpty() = runBlocking {
        val params = SignPdfParams(
            inputUri = TestUri("content://test/doc.pdf"),
            baseFileName = "doc.pdf",
            signatures = emptyList()
        )

        val result = signPdfUseCase(params)
        assertTrue("Expected failure when signatures list is empty", result.isFailure)
        assertEquals("Please add at least one signature to sign the PDF.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testSignPdfSuccessWithSignatures() = runBlocking {
        val sigSpec = SignatureSpec(
            id = "sig1",
            pageIndex = 0,
            normalizedX = 0.2f,
            normalizedY = 0.3f,
            normalizedWidth = 0.4f,
            normalizedHeight = 0.2f
        )

        val params = SignPdfParams(
            inputUri = TestUri("content://test/doc.pdf"),
            baseFileName = "doc.pdf",
            signatures = listOf(sigSpec)
        )

        val result = signPdfUseCase(params)
        assertTrue("Expected success when signature is provided", result.isSuccess)
        assertEquals("doc_signed.pdf", result.getOrNull()?.displayName)
    }

    private class FakePdfRepository : PdfRepository {
        override suspend fun getPdfFileInfo(uri: Uri): PdfFile {
            return PdfFile(
                uri = uri,
                displayName = "doc.pdf",
                size = 2048L,
                pageCount = 3,
                isValid = true
            )
        }

        override suspend fun mergePdfs(inputUris: List<Uri>, outputStream: OutputStream): Result<Int> {
            return Result.failure(NotImplementedError())
        }

        override suspend fun createTempMergedFile(inputUris: List<Uri>): Result<File> {
            return Result.failure(NotImplementedError())
        }

        override suspend fun splitPdf(
            inputUri: Uri,
            baseFileName: String,
            splitMode: SplitMode,
            pageGroups: List<List<Int>>
        ): Result<List<SplitOutputFile>> {
            return Result.failure(NotImplementedError())
        }

        override suspend fun organizePdf(
            inputUri: Uri,
            baseFileName: String,
            pageSpecs: List<OrganizePageSpec>
        ): Result<OrganizeOutputFile> {
            return Result.failure(NotImplementedError())
        }

        override suspend fun compressPdf(
            inputUri: Uri,
            baseFileName: String,
            level: CompressionLevel
        ): Result<CompressPdfResult> {
            return Result.failure(NotImplementedError())
        }

        override suspend fun signPdf(params: SignPdfParams): Result<SignOutputFile> {
            if (params.signatures.isEmpty()) {
                return Result.failure(IllegalArgumentException("Please add at least one signature to sign the PDF."))
            }
            val fakeFile = File.createTempFile("doc_signed_", ".pdf")
            return Result.success(
                SignOutputFile(
                    file = fakeFile,
                    uri = params.inputUri,
                    displayName = "doc_signed.pdf",
                    size = 2048L,
                    pageCount = 3
                )
            )
        }
    }
}
