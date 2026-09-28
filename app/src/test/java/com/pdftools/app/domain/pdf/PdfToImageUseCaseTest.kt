package com.pdftools.app.domain.pdf

import android.net.TestUri
import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream

class PdfToImageUseCaseTest {

    private lateinit var fakeRepository: FakePdfRepository
    private lateinit var useCase: PdfToImageUseCase
    private val testUri = TestUri("content://test/Annual_Report.pdf")

    @Before
    fun setUp() {
        fakeRepository = FakePdfRepository()
        useCase = PdfToImageUseCase(fakeRepository, Dispatchers.Unconfined)
    }

    @Test
    fun testOnePagePdfToJpg() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "Annual_Report.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue("Expected success for one page PDF to JPG", result.isSuccess)
        val res = result.getOrNull()!!
        assertEquals(1, res.outputFiles.size)
        assertEquals("Annual_Report_page_001.jpg", res.outputFiles[0].displayName)
        assertEquals(0, res.outputFiles[0].pageIndex)
    }

    @Test
    fun testMultiPagePdfToMultipleJpgs() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "Annual_Report.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0, 1, 2)
        )

        val result = useCase(params)
        assertTrue("Expected success for multi-page PDF", result.isSuccess)
        val res = result.getOrNull()!!
        assertEquals(3, res.outputFiles.size)
        assertEquals("Annual_Report_page_001.jpg", res.outputFiles[0].displayName)
        assertEquals("Annual_Report_page_002.jpg", res.outputFiles[1].displayName)
        assertEquals("Annual_Report_page_003.jpg", res.outputFiles[2].displayName)
    }

    @Test
    fun testPdfToPng() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "Annual_Report.pdf",
            format = PdfToImageFormat.PNG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0, 1)
        )

        val result = useCase(params)
        assertTrue("Expected success for PNG export", result.isSuccess)
        val res = result.getOrNull()!!
        assertEquals(2, res.outputFiles.size)
        assertEquals("Annual_Report_page_001.png", res.outputFiles[0].displayName)
        assertEquals("Annual_Report_page_002.png", res.outputFiles[1].displayName)
    }

    @Test
    fun testHighJpgQuality() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "doc.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals(PdfToImageQuality.HIGH, fakeRepository.lastParams?.quality)
    }

    @Test
    fun testBalancedJpgQuality() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "doc.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.BALANCED,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals(PdfToImageQuality.BALANCED, fakeRepository.lastParams?.quality)
    }

    @Test
    fun testSmallJpgQuality() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "doc.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.SMALL,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals(PdfToImageQuality.SMALL, fakeRepository.lastParams?.quality)
    }

    @Test
    fun testAllPagesSelection() = runBlocking {
        val allPages = (0 until 10).toList()
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "doc.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = allPages
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.outputFiles?.size)
    }

    @Test
    fun testSelectedPages() = runBlocking {
        val selectedPages = listOf(0, 2, 6)
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "doc.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = selectedPages
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        val res = result.getOrNull()!!
        assertEquals(3, res.outputFiles.size)
        assertEquals(0, res.outputFiles[0].pageIndex)
        assertEquals(2, res.outputFiles[1].pageIndex)
        assertEquals(6, res.outputFiles[2].pageIndex)
    }

    @Test
    fun testPageRanges() = runBlocking {
        val parsed = PageRangeParser.parseRanges("1-5", 10)
        assertTrue(parsed is PageRangeParseResult.Success)
        val pages = (parsed as PageRangeParseResult.Success).pageGroups.flatten()

        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "doc.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = pages
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull()?.outputFiles?.size)
    }

    @Test
    fun testInvalidPageRange() = runBlocking {
        val parsed = PageRangeParser.parseRanges("1-20", 5)
        assertTrue(parsed is PageRangeParseResult.Error)
        val err = (parsed as PageRangeParseResult.Error).message
        assertTrue(err.contains("exceeds total page count"))
    }

    @Test
    fun testPortraitAndLandscapeAndRotatedPages() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "mixed_orientations.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.HIGH_300,
            pagesToRender = listOf(0, 1, 2)
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.outputFiles?.size)
    }

    @Test
    fun test100PlusPagePdf() = runBlocking {
        val largePages = (0 until 120).toList()
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "large_book.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.BALANCED,
            resolution = PdfToImageResolution.DPI_150,
            pagesToRender = largePages
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals(120, result.getOrNull()?.outputFiles?.size)
    }

    @Test
    fun testLargeScannedPdf() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "scanned_doc.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.SMALL,
            resolution = PdfToImageResolution.DPI_100,
            pagesToRender = listOf(0, 1)
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.outputFiles?.size)
    }

    @Test
    fun testOutputFilenamesAndContentUris() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "Annual_Report.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        val outputFile = result.getOrNull()?.outputFiles?.first()!!
        assertEquals("Annual_Report_page_001.jpg", outputFile.displayName)
        assertNotNull(outputFile.uri)
        assertTrue(outputFile.uri.toString().contains("content://"))
    }

    @Test
    fun testOriginalPdfRemainsUnchanged() = runBlocking {
        val originalFile = File.createTempFile("original_", ".pdf")
        originalFile.writeText("Dummy PDF contents")
        val originalLength = originalFile.length()

        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "Annual_Report.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue(result.isSuccess)
        assertEquals("Original file should remain unchanged", originalLength, originalFile.length())
        originalFile.delete()
        Unit
    }

    @Test
    fun testEmptyPagesSelectionFails() = runBlocking {
        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "Annual_Report.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = emptyList()
        )

        val result = useCase(params)
        assertTrue("Expected failure for empty page selection", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("select at least one page") == true)
    }

    @Test
    fun testStorageFailureHandled() = runBlocking {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = IOException("Insufficient storage space")

        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "Annual_Report.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Insufficient storage") == true)
    }

    @Test
    fun testPasswordProtectedPdfHandled() = runBlocking {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = IllegalStateException("This PDF is password protected.")

        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "protected.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("password protected") == true)
    }

    @Test
    fun testCorruptedPdfHandled() = runBlocking {
        fakeRepository.shouldFail = true
        fakeRepository.failureException = IOException("Unable to read source PDF file.")

        val params = PdfToImageParams(
            inputUri = testUri,
            baseFileName = "corrupted.pdf",
            format = PdfToImageFormat.JPG,
            quality = PdfToImageQuality.HIGH,
            resolution = PdfToImageResolution.DPI_200,
            pagesToRender = listOf(0)
        )

        val result = useCase(params)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unable to read") == true)
    }

    private class FakePdfRepository : PdfRepository {
        var shouldFail = false
        var failureException: Exception = IOException("Error")
        var lastParams: PdfToImageParams? = null
        val testOutputUri = TestUri("content://test/output_image.jpg")

        override suspend fun getPdfFileInfo(uri: Uri): PdfFile {
            return PdfFile(
                uri = uri,
                displayName = "test.pdf",
                size = 1000000L,
                pageCount = 120,
                isValid = true
            )
        }

        override suspend fun convertPdfToImages(
            params: PdfToImageParams,
            onProgress: ((current: Int, total: Int) -> Unit)?
        ): Result<PdfToImageResult> {
            lastParams = params
            if (shouldFail) {
                return Result.failure(failureException)
            }

            val rawBaseName = params.baseFileName
            val cleanName = if (rawBaseName.endsWith(".pdf", ignoreCase = true)) {
                rawBaseName.substring(0, rawBaseName.length - 4)
            } else {
                rawBaseName
            }
            val ext = params.format.extension

            val outputs = params.pagesToRender.mapIndexed { idx, pageIdx ->
                onProgress?.invoke(idx + 1, params.pagesToRender.size)
                val fileName = "${cleanName}_page_${String.format("%03d", pageIdx + 1)}.$ext"
                val f = File.createTempFile("fake_img_", ".$ext")
                PdfToImageOutputFile(
                    file = f,
                    uri = TestUri("content://test/$fileName"),
                    displayName = fileName,
                    size = 204800L,
                    pageIndex = pageIdx
                )
            }

            return Result.success(
                PdfToImageResult(
                    outputFiles = outputs,
                    totalInputPages = 120
                )
            )
        }

        override suspend fun mergePdfs(inputUris: List<Uri>, outputStream: OutputStream): Result<Int> {
            return Result.success(1)
        }

        override suspend fun createTempMergedFile(inputUris: List<Uri>): Result<File> {
            return Result.success(File.createTempFile("fake_", ".pdf"))
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
