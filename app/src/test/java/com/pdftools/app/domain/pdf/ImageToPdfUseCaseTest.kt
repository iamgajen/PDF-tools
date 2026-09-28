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

class ImageToPdfUseCaseTest {

    private lateinit var fakeRepository: FakePdfRepository
    private lateinit var useCase: ImageToPdfUseCase

    @Before
    fun setUp() {
        fakeRepository = FakePdfRepository()
        useCase = ImageToPdfUseCase(fakeRepository, Dispatchers.Unconfined)
    }

    @Test
    fun testRejectEmptyImageSelection() = runBlocking {
        val result = useCase(
            images = emptyList(),
            baseFileName = "test"
        )
        assertTrue("Expected failure for empty images list", result.isFailure)
        assertEquals("Please select at least one image to create a PDF.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testConvertSingleImageSuccess() = runBlocking {
        val images = listOf(ImageSpec(uri = TestUri("content://test/image1.jpg"), name = "image1.jpg"))
        val result = useCase(
            images = images,
            baseFileName = "holiday_001",
            pageSize = ImageToPdfPageSize.A4,
            quality = ImageToPdfQuality.HIGH
        )
        assertTrue("Expected success for valid single image", result.isSuccess)
        val pdfResult = result.getOrNull()
        assertEquals("holiday_001.pdf", pdfResult?.displayName)
        assertEquals(1, pdfResult?.pageCount)
    }

    @Test
    fun testConvertMultipleImagesSuccess() = runBlocking {
        val images = listOf(
            ImageSpec(uri = TestUri("content://test/img1.png"), name = "img1.png"),
            ImageSpec(uri = TestUri("content://test/img2.jpg"), name = "img2.jpg"),
            ImageSpec(uri = TestUri("content://test/img3.webp"), name = "img3.webp")
        )
        fakeRepository.nextPageCount = 3
        val result = useCase(
            images = images,
            baseFileName = "vacation.pdf",
            pageSize = ImageToPdfPageSize.LETTER,
            quality = ImageToPdfQuality.BALANCED
        )
        assertTrue("Expected success for multiple images", result.isSuccess)
        val pdfResult = result.getOrNull()
        assertEquals("vacation.pdf", pdfResult?.displayName)
        assertEquals(3, pdfResult?.pageCount)
    }

    private class FakePdfRepository : PdfRepository {
        var nextPageCount = 1

        override suspend fun getPdfFileInfo(uri: Uri): PdfFile {
            return PdfFile(
                uri = uri,
                displayName = "test.pdf",
                size = 1024L,
                pageCount = 1,
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

        override suspend fun convertImagesToPdf(
            images: List<ImageSpec>,
            baseFileName: String,
            pageSize: ImageToPdfPageSize,
            quality: ImageToPdfQuality,
            onProgress: ((current: Int, total: Int) -> Unit)?
        ): Result<ImageToPdfResult> {
            val fakeFile = File.createTempFile("fake_created_", ".pdf")
            return Result.success(
                ImageToPdfResult(
                    file = fakeFile,
                    uri = TestUri(fakeFile.toURI().toString()),
                    displayName = baseFileName,
                    size = 2048L,
                    pageCount = nextPageCount
                )
            )
        }
    }
}
