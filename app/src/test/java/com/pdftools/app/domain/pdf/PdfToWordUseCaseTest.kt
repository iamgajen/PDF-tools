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

class PdfToWordUseCaseTest {

    private lateinit var fakeRepository: FakePdfRepository
    private lateinit var useCase: PdfToWordUseCase

    @Before
    fun setUp() {
        fakeRepository = FakePdfRepository()
        useCase = PdfToWordUseCase(fakeRepository)
    }

    @Test
    fun testPdfToWordUseCaseSuccess() = runBlocking {
        val testUri = TestUri("content://test/document.pdf")
        val result = useCase(testUri, "document.pdf")

        assertTrue(result.isSuccess)
        val wordResult = result.getOrThrow()
        assertEquals("document.docx", wordResult.displayName)
        assertEquals(3, wordResult.pageCount)
    }

    @Test
    fun testPdfToWordUseCaseFailure() = runBlocking {
        fakeRepository.shouldFail = true
        val testUri = TestUri("content://test/document.pdf")
        val result = useCase(testUri, "document.pdf")

        assertTrue(result.isFailure)
        assertEquals("Conversion failed", result.exceptionOrNull()?.message)
    }

    private class FakePdfRepository : PdfRepository {
        var shouldFail = false

        override suspend fun getPdfFileInfo(uri: Uri): PdfFile {
            return PdfFile(uri = uri, displayName = "doc.pdf", size = 1000L, isValid = true)
        }

        override suspend fun mergePdfs(inputUris: List<Uri>, outputStream: OutputStream): Result<Int> = Result.success(1)
        override suspend fun createTempMergedFile(inputUris: List<Uri>): Result<File> = Result.success(File("fake"))
        override suspend fun splitPdf(inputUri: Uri, baseFileName: String, splitMode: SplitMode, pageGroups: List<List<Int>>): Result<List<SplitOutputFile>> = Result.success(emptyList())
        override suspend fun organizePdf(inputUri: Uri, baseFileName: String, pageSpecs: List<OrganizePageSpec>): Result<OrganizeOutputFile> = Result.failure(NotImplementedError())
        override suspend fun compressPdf(inputUri: Uri, baseFileName: String, level: CompressionLevel): Result<CompressPdfResult> = Result.failure(NotImplementedError())

        override suspend fun convertPdfToWord(
            inputUri: Uri,
            baseFileName: String,
            onProgress: ((current: Int, total: Int) -> Unit)?
        ): Result<PdfToWordResult> {
            if (shouldFail) {
                return Result.failure(Exception("Conversion failed"))
            }
            val fakeFile = File.createTempFile("fake_pdf_word_", ".docx")
            return Result.success(
                PdfToWordResult(
                    file = fakeFile,
                    uri = inputUri,
                    displayName = "document.docx",
                    size = 3072L,
                    pageCount = 3
                )
            )
        }
    }
}
