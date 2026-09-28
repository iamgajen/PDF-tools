package com.pdftools.app.domain.pdf

import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

class MergePdfUseCaseTest {

    private lateinit var mockRepository: FakePdfRepository
    private lateinit var mergePdfUseCase: MergePdfUseCase

    @Before
    fun setUp() {
        mockRepository = FakePdfRepository()
        mergePdfUseCase = MergePdfUseCase(mockRepository, Dispatchers.Unconfined)
    }

    @Test
    fun testRejectLessThanTwoPdfs() = runBlocking {
        val singleUri = listOf(Uri.parse("content://test/1.pdf"))
        val outputStream = ByteArrayOutputStream()

        val result = mergePdfUseCase(singleUri, outputStream)
        assertTrue("Expected failure for less than 2 PDFs", result.isFailure)
        assertEquals("Please select at least two PDF files to merge.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testMergeTwoPdfsSuccess() = runBlocking {
        val uris = listOf(
            Uri.parse("content://test/1.pdf"),
            Uri.parse("content://test/2.pdf")
        )
        val outputStream = ByteArrayOutputStream()

        val result = mergePdfUseCase(uris, outputStream)
        assertTrue("Expected success when 2 URIs provided", result.isSuccess)
        assertEquals(10, result.getOrNull())
    }

    private class FakePdfRepository : PdfRepository {
        override suspend fun getPdfFileInfo(uri: Uri): PdfFile {
            return PdfFile(
                uri = uri,
                displayName = "test.pdf",
                size = 1024L,
                pageCount = 5,
                isValid = true
            )
        }

        override suspend fun mergePdfs(inputUris: List<Uri>, outputStream: OutputStream): Result<Int> {
            return Result.success(10)
        }

        override suspend fun createTempMergedFile(inputUris: List<Uri>): Result<File> {
            return Result.success(File.createTempFile("fake_merged_", ".pdf"))
        }

        override suspend fun splitPdf(
            inputUri: Uri,
            baseFileName: String,
            splitMode: SplitMode,
            pageGroups: List<List<Int>>
        ): Result<List<SplitOutputFile>> {
            return Result.success(emptyList())
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
    }
}
