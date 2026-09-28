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

class OrganizePdfUseCaseTest {

    private lateinit var fakeRepository: FakePdfRepository
    private lateinit var organizePdfUseCase: OrganizePdfUseCase
    private val testUri = TestUri("content://test/doc.pdf")

    @Before
    fun setUp() {
        fakeRepository = FakePdfRepository()
        organizePdfUseCase = OrganizePdfUseCase(fakeRepository, Dispatchers.Unconfined)
    }

    @Test
    fun testOrganizePdfSuccess() = runBlocking {
        val specs = listOf(
            OrganizePageSpec(originalPageIndex = 2, rotationDegrees = 90),
            OrganizePageSpec(originalPageIndex = 0, rotationDegrees = 0)
        )
        val result = organizePdfUseCase(testUri, "doc.pdf", specs)
        assertTrue("Expected success for valid page specs", result.isSuccess)
        val output = result.getOrNull()
        assertEquals(2, output?.pageCount)
        assertEquals(specs, fakeRepository.lastPageSpecs)
    }

    @Test
    fun testOrganizePdfEmptySpecsError() = runBlocking {
        val result = organizePdfUseCase(testUri, "doc.pdf", emptyList())
        assertTrue("Expected failure for empty specs", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception?.message?.contains("at least one page") == true)
    }

    private class FakePdfRepository : PdfRepository {
        var lastPageSpecs: List<OrganizePageSpec>? = null
        val testOutputUri = TestUri("content://test/organized_output.pdf")

        override suspend fun getPdfFileInfo(uri: Uri): PdfFile {
            return PdfFile(
                uri = uri,
                displayName = "test.pdf",
                size = 1024L,
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
            lastPageSpecs = pageSpecs
            val file = File.createTempFile("fake_organized_", ".pdf")
            val output = OrganizeOutputFile(
                file = file,
                uri = testOutputUri,
                displayName = "test_organized.pdf",
                size = 600L,
                pageCount = pageSpecs.size
            )
            return Result.success(output)
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
