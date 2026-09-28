package com.pdftools.app.domain.pdf

import android.net.TestUri
import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream

class SplitPdfUseCaseTest {

    private lateinit var fakeRepository: FakePdfRepository
    private lateinit var splitPdfUseCase: SplitPdfUseCase
    private val testUri = TestUri("content://test/doc.pdf")

    @Before
    fun setUp() {
        fakeRepository = FakePdfRepository()
        splitPdfUseCase = SplitPdfUseCase(fakeRepository, Dispatchers.Unconfined)
    }

    @Test
    fun testSplitEveryPageSuccess() = runBlocking {
        val result = splitPdfUseCase(testUri, "doc.pdf", SplitMode.EveryPage, pageCount = 3)
        assertTrue("Expected success for EveryPage mode", result.isSuccess)
        val files = result.getOrNull()
        assertEquals(3, files?.size)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), fakeRepository.lastPageGroups)
    }

    @Test
    fun testSplitByRangesSuccess() = runBlocking {
        val mode = SplitMode.ByRanges("1-2, 3-5")
        val result = splitPdfUseCase(testUri, "doc.pdf", mode, pageCount = 5)
        assertTrue("Expected success for ByRanges mode", result.isSuccess)
        val files = result.getOrNull()
        assertEquals(2, files?.size)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3, 4)), fakeRepository.lastPageGroups)
    }

    @Test
    fun testExtractPagesSuccess() = runBlocking {
        val mode = SplitMode.ExtractPages("1, 3, 5")
        val result = splitPdfUseCase(testUri, "doc.pdf", mode, pageCount = 5)
        assertTrue("Expected success for ExtractPages mode", result.isSuccess)
        val files = result.getOrNull()
        assertEquals(1, files?.size)
        assertEquals(listOf(listOf(0, 2, 4)), fakeRepository.lastPageGroups)
    }

    @Test
    fun testSplitInvalidRangeError() = runBlocking {
        val mode = SplitMode.ByRanges("1-10") // exceeds 5 pages
        val result = splitPdfUseCase(testUri, "doc.pdf", mode, pageCount = 5)
        assertTrue("Expected failure for out of range input", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception?.message?.contains("exceeds total page count") == true)
    }

    @Test
    fun testSplitMalformedRangeError() = runBlocking {
        val mode = SplitMode.ByRanges("abc")
        val result = splitPdfUseCase(testUri, "doc.pdf", mode, pageCount = 5)
        assertTrue("Expected failure for malformed input", result.isFailure)
    }

    private class FakePdfRepository : PdfRepository {
        var lastPageGroups: List<List<Int>>? = null
        val testOutputUri = TestUri("content://test/output.pdf")

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
            lastPageGroups = pageGroups
            val outputs = pageGroups.mapIndexed { index, group ->
                val f = File.createTempFile("fake_split_", ".pdf")
                SplitOutputFile(
                    file = f,
                    uri = testOutputUri,
                    displayName = "split_$index.pdf",
                    size = 500L,
                    pageCount = group.size
                )
            }
            return Result.success(outputs)
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
