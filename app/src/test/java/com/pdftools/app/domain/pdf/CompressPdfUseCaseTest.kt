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

class CompressPdfUseCaseTest {

    private lateinit var fakeRepository: FakePdfRepository
    private lateinit var compressPdfUseCase: CompressPdfUseCase
    private val testUri = TestUri("content://test/doc.pdf")

    @Before
    fun setUp() {
        fakeRepository = FakePdfRepository()
        compressPdfUseCase = CompressPdfUseCase(fakeRepository, Dispatchers.Unconfined)
    }

    @Test
    fun testCompressPdfSuccess() = runBlocking {
        fakeRepository.shouldSimulateReduction = true
        val result = compressPdfUseCase(testUri, "doc.pdf", CompressionLevel.BALANCED)
        assertTrue("Expected success for compress PDF", result.isSuccess)
        val compressResult = result.getOrNull()
        assertTrue(compressResult is CompressPdfResult.Success)

        val success = compressResult as CompressPdfResult.Success
        assertEquals(1000000L, success.originalSize)
        assertEquals(400000L, success.compressedSize)
        assertEquals(60, success.percentageSaved)
        assertEquals(3, success.pageCount)
    }

    @Test
    fun testCompressPdfAlreadyOptimized() = runBlocking {
        fakeRepository.shouldSimulateReduction = false
        val result = compressPdfUseCase(testUri, "doc.pdf", CompressionLevel.HIGH_QUALITY)
        assertTrue("Expected success for already optimized PDF", result.isSuccess)
        val compressResult = result.getOrNull()
        assertTrue(compressResult is CompressPdfResult.AlreadyOptimized)

        val optimized = compressResult as CompressPdfResult.AlreadyOptimized
        assertEquals(1000000L, optimized.originalSize)
        assertEquals(3, optimized.pageCount)
    }

    private class FakePdfRepository : PdfRepository {
        var shouldSimulateReduction = true
        val testOutputUri = TestUri("content://test/compressed_output.pdf")

        override suspend fun getPdfFileInfo(uri: Uri): PdfFile {
            return PdfFile(
                uri = uri,
                displayName = "test.pdf",
                size = 1000000L,
                pageCount = 3,
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
            val file = File.createTempFile("fake_compressed_", ".pdf")
            return if (shouldSimulateReduction) {
                Result.success(
                    CompressPdfResult.Success(
                        file = file,
                        uri = testOutputUri,
                        displayName = "test_compressed.pdf",
                        originalSize = 1000000L,
                        compressedSize = 400000L,
                        pageCount = 3
                    )
                )
            } else {
                Result.success(
                    CompressPdfResult.AlreadyOptimized(
                        file = file,
                        uri = inputUri,
                        displayName = "test.pdf",
                        originalSize = 1000000L,
                        pageCount = 3
                    )
                )
            }
        }
    }
}
