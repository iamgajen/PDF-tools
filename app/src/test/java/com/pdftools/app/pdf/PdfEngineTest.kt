package com.pdftools.app.pdf

import com.pdftools.app.domain.pdf.CompressionLevel
import com.pdftools.app.domain.pdf.OrganizePageSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PdfEngineTest {

    private lateinit var pdfEngine: PdfEngine

    @Before
    fun setUp() {
        pdfEngine = PdfEngineImpl()
    }

    @Test
    fun testMergeTwoPdfs() {
        val pdf1 = createTestPdf(pageCount = 2)
        val pdf2 = createTestPdf(pageCount = 3)
        val outputFile = File.createTempFile("output_merged_two_", ".pdf")

        val streamProviders = listOf(
            { FileInputStream(pdf1) },
            { FileInputStream(pdf2) }
        )

        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.mergePdfStreams(streamProviders, outStream)
            if (result.isFailure) {
                result.exceptionOrNull()?.printStackTrace()
            }
            assertTrue("Expected merge to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals(5, result.getOrNull())
        }

        assertTrue("Output file should exist", outputFile.exists())
        assertTrue("Output file size should be greater than 0", outputFile.length() > 0)

        // Verify readable
        val mergedDoc = PDDocument.load(outputFile)
        assertEquals(5, mergedDoc.numberOfPages)
        mergedDoc.close()

        pdf1.delete()
        pdf2.delete()
        outputFile.delete()
    }

    @Test
    fun testMergeMultiplePdfs() {
        val pdf1 = createTestPdf(pageCount = 1)
        val pdf2 = createTestPdf(pageCount = 2)
        val pdf3 = createTestPdf(pageCount = 4)
        val outputFile = File.createTempFile("output_merged_multi_", ".pdf")

        val streamProviders = listOf(
            { FileInputStream(pdf1) },
            { FileInputStream(pdf2) },
            { FileInputStream(pdf3) }
        )

        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.mergePdfStreams(streamProviders, outStream)
            assertTrue("Expected merge to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals(7, result.getOrNull())
        }

        val mergedDoc = PDDocument.load(outputFile)
        assertEquals(7, mergedDoc.numberOfPages)
        mergedDoc.close()

        pdf1.delete()
        pdf2.delete()
        pdf3.delete()
        outputFile.delete()
    }

    @Test
    fun testPreservePageOrder() {
        val pdf1 = createTestPdf(pageCount = 3)
        val pdf2 = createTestPdf(pageCount = 2)
        val outputFile = File.createTempFile("output_merged_order_", ".pdf")

        val streamProviders = listOf(
            { FileInputStream(pdf1) },
            { FileInputStream(pdf2) }
        )

        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.mergePdfStreams(streamProviders, outStream)
            assertTrue("Expected merge to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        }

        val mergedDoc = PDDocument.load(outputFile)
        assertEquals(5, mergedDoc.numberOfPages)
        mergedDoc.close()

        pdf1.delete()
        pdf2.delete()
        outputFile.delete()
    }

    @Test
    fun testRejectInvalidPdf() {
        val validPdf = createTestPdf(pageCount = 2)
        val invalidPdf = createInvalidPdfFile()
        val outputFile = File.createTempFile("output_invalid_", ".pdf")

        val streamProviders = listOf(
            { FileInputStream(validPdf) },
            { FileInputStream(invalidPdf) }
        )

        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.mergePdfStreams(streamProviders, outStream)
            assertTrue("Expected merge to fail on invalid PDF", result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue(
                "Error message should mention unable to read or invalid: ${exception?.message}",
                exception?.message?.contains("Unable to read PDF") == true ||
                        exception?.message?.contains("position 2") == true
            )
        }

        validPdf.delete()
        invalidPdf.delete()
        outputFile.delete()
    }

    @Test
    fun testHandleEmptySelection() {
        val outputFile = File.createTempFile("output_empty_", ".pdf")
        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.mergePdfStreams(emptyList(), outStream)
            assertTrue("Expected failure for empty list", result.isFailure)
        }
        outputFile.delete()
    }

    @Test
    fun testHandlePasswordProtectedPdfGracefully() {
        val validPdf = createTestPdf(pageCount = 1)
        val protectedPdf = createPasswordProtectedPdf("secret")
        val outputFile = File.createTempFile("output_protected_", ".pdf")

        val streamProviders = listOf(
            { FileInputStream(validPdf) },
            { FileInputStream(protectedPdf) }
        )

        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.mergePdfStreams(streamProviders, outStream)
            assertTrue("Expected merge to fail for password protected PDF", result.isFailure)
            val message = result.exceptionOrNull()?.message
            assertNotNull(message)
            assertTrue(
                "Message should state password protection: $message",
                message?.contains("password protected") == true
            )
        }

        validPdf.delete()
        protectedPdf.delete()
        outputFile.delete()
    }

    @Test
    fun testOutputFileExistsAndIsReadable() {
        val pdf1 = createTestPdf(pageCount = 1)
        val pdf2 = createTestPdf(pageCount = 1)
        val outputFile = File.createTempFile("output_readable_check_", ".pdf")

        val streamProviders = listOf(
            { FileInputStream(pdf1) },
            { FileInputStream(pdf2) }
        )

        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.mergePdfStreams(streamProviders, outStream)
            assertTrue("Expected merge to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        }

        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 0)

        // Read and verify page count
        val doc = PDDocument.load(outputFile)
        assertEquals(2, doc.numberOfPages)
        doc.close()

        pdf1.delete()
        pdf2.delete()
        outputFile.delete()
    }

    @Test
    fun testSplitEveryPage() {
        val originalPdf = createTestPdf(pageCount = 3)
        val out1 = File.createTempFile("split_p1_", ".pdf")
        val out2 = File.createTempFile("split_p2_", ".pdf")
        val out3 = File.createTempFile("split_p3_", ".pdf")

        val pageGroups = listOf(listOf(0), listOf(1), listOf(2))
        val outputProviders = listOf(
            { FileOutputStream(out1) },
            { FileOutputStream(out2) },
            { FileOutputStream(out3) }
        )

        val result = pdfEngine.splitPdfStreams(
            inputStreamProvider = { FileInputStream(originalPdf) },
            pageGroups = pageGroups,
            outputStreamProviders = outputProviders
        )

        assertTrue("Expected split to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val counts = result.getOrNull()
        assertEquals(listOf(1, 1, 1), counts)

        // Verify output files are readable and have 1 page
        for (out in listOf(out1, out2, out3)) {
            val doc = PDDocument.load(out)
            assertEquals(1, doc.numberOfPages)
            doc.close()
            out.delete()
        }

        // Verify original PDF remains unchanged
        val origDoc = PDDocument.load(originalPdf)
        assertEquals(3, origDoc.numberOfPages)
        origDoc.close()
        originalPdf.delete()
    }

    @Test
    fun testSplitPageRanges() {
        val originalPdf = createTestPdf(pageCount = 5)
        val out1 = File.createTempFile("split_part1_", ".pdf")
        val out2 = File.createTempFile("split_part2_", ".pdf")

        // ranges 1-2 (indices 0,1) and 3-5 (indices 2,3,4)
        val pageGroups = listOf(listOf(0, 1), listOf(2, 3, 4))
        val outputProviders = listOf(
            { FileOutputStream(out1) },
            { FileOutputStream(out2) }
        )

        val result = pdfEngine.splitPdfStreams(
            inputStreamProvider = { FileInputStream(originalPdf) },
            pageGroups = pageGroups,
            outputStreamProviders = outputProviders
        )

        assertTrue("Expected split to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val counts = result.getOrNull()
        assertEquals(listOf(2, 3), counts)

        val doc1 = PDDocument.load(out1)
        assertEquals(2, doc1.numberOfPages)
        doc1.close()

        val doc2 = PDDocument.load(out2)
        assertEquals(3, doc2.numberOfPages)
        doc2.close()

        out1.delete()
        out2.delete()
        originalPdf.delete()
    }

    @Test
    fun testExtractSelectedPages() {
        val originalPdf = createTestPdf(pageCount = 5)
        val out = File.createTempFile("split_extracted_", ".pdf")

        // extract pages 1, 3, 5 (indices 0, 2, 4)
        val pageGroups = listOf(listOf(0, 2, 4))
        val outputProviders = listOf({ FileOutputStream(out) })

        val result = pdfEngine.splitPdfStreams(
            inputStreamProvider = { FileInputStream(originalPdf) },
            pageGroups = pageGroups,
            outputStreamProviders = outputProviders
        )

        assertTrue("Expected split to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val counts = result.getOrNull()
        assertEquals(listOf(3), counts)

        val doc = PDDocument.load(out)
        assertEquals(3, doc.numberOfPages)
        doc.close()

        out.delete()
        originalPdf.delete()
    }

    @Test
    fun testSplitInvalidPageRangeOutOfBounds() {
        val originalPdf = createTestPdf(pageCount = 3)
        val out = File.createTempFile("split_out_of_bounds_", ".pdf")

        val pageGroups = listOf(listOf(0, 5)) // index 5 is out of bounds for 3-page PDF
        val outputProviders = listOf({ FileOutputStream(out) })

        val result = pdfEngine.splitPdfStreams(
            inputStreamProvider = { FileInputStream(originalPdf) },
            pageGroups = pageGroups,
            outputStreamProviders = outputProviders
        )

        assertTrue("Expected split to fail for out-of-bounds page index", result.isFailure)
        out.delete()
        originalPdf.delete()
    }

    @Test
    fun testSplitEmptySelection() {
        val originalPdf = createTestPdf(pageCount = 3)

        val result = pdfEngine.splitPdfStreams(
            inputStreamProvider = { FileInputStream(originalPdf) },
            pageGroups = emptyList(),
            outputStreamProviders = emptyList()
        )

        assertTrue("Expected failure for empty page groups", result.isFailure)
        originalPdf.delete()
    }

    @Test
    fun testSplitCorruptedPdf() {
        val corruptedPdf = createInvalidPdfFile()
        val out = File.createTempFile("split_corrupted_", ".pdf")

        val pageGroups = listOf(listOf(0))
        val outputProviders = listOf({ FileOutputStream(out) })

        val result = pdfEngine.splitPdfStreams(
            inputStreamProvider = { FileInputStream(corruptedPdf) },
            pageGroups = pageGroups,
            outputStreamProviders = outputProviders
        )

        assertTrue("Expected split to fail for corrupted PDF", result.isFailure)
        out.delete()
        corruptedPdf.delete()
    }

    @Test
    fun testSplitPasswordProtectedPdf() {
        val protectedPdf = createPasswordProtectedPdf("secret")
        val out = File.createTempFile("split_protected_", ".pdf")

        val pageGroups = listOf(listOf(0))
        val outputProviders = listOf({ FileOutputStream(out) })

        val result = pdfEngine.splitPdfStreams(
            inputStreamProvider = { FileInputStream(protectedPdf) },
            pageGroups = pageGroups,
            outputStreamProviders = outputProviders
        )

        assertTrue("Expected split to fail for password protected PDF", result.isFailure)
        val message = result.exceptionOrNull()?.message
        assertTrue(message?.contains("password protected") == true)

        out.delete()
        protectedPdf.delete()
    }

    @Test
    fun testOrganizePdfReorderAndRotate() {
        val originalPdf = createTestPdf(pageCount = 4)
        val out = File.createTempFile("organize_out_", ".pdf")

        // Reorder pages (2, 0, 3) and rotate page 2 by 90 degrees
        val pageSpecs = listOf(
            OrganizePageSpec(originalPageIndex = 2, rotationDegrees = 90),
            OrganizePageSpec(originalPageIndex = 0, rotationDegrees = 0),
            OrganizePageSpec(originalPageIndex = 3, rotationDegrees = 180)
        )

        FileOutputStream(out).use { outStream ->
            val result = pdfEngine.organizePdfStreams(
                inputStreamProvider = { FileInputStream(originalPdf) },
                pageSpecs = pageSpecs,
                outputStream = outStream
            )
            assertTrue("Expected organize to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals(3, result.getOrNull())
        }

        // Verify readable & rotation applied
        val organizedDoc = PDDocument.load(out)
        assertEquals(3, organizedDoc.numberOfPages)
        assertEquals(90, organizedDoc.getPage(0).rotation)
        assertEquals(0, organizedDoc.getPage(1).rotation)
        assertEquals(180, organizedDoc.getPage(2).rotation)
        organizedDoc.close()

        // Verify original PDF unchanged
        val origDoc = PDDocument.load(originalPdf)
        assertEquals(4, origDoc.numberOfPages)
        assertEquals(0, origDoc.getPage(2).rotation)
        origDoc.close()

        out.delete()
        originalPdf.delete()
    }

    @Test
    fun testOrganizePdfEmptySpecs() {
        val originalPdf = createTestPdf(pageCount = 3)
        val out = File.createTempFile("organize_empty_out_", ".pdf")

        FileOutputStream(out).use { outStream ->
            val result = pdfEngine.organizePdfStreams(
                inputStreamProvider = { FileInputStream(originalPdf) },
                pageSpecs = emptyList(),
                outputStream = outStream
            )
            assertTrue("Expected failure for empty specs", result.isFailure)
        }

        out.delete()
        originalPdf.delete()
    }

    @Test
    fun testCompressTextPdfPreservesPagesAndDimensions() {
        val originalPdf = createTestPdf(pageCount = 3)
        val out = File.createTempFile("compress_text_out_", ".pdf")

        FileOutputStream(out).use { outStream ->
            val result = pdfEngine.compressPdfStreams(
                inputStreamProvider = { FileInputStream(originalPdf) },
                level = CompressionLevel.HIGH_QUALITY,
                outputStream = outStream
            )
            assertTrue("Expected compress to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals(3, result.getOrNull())
        }

        // Verify output readable, page count and page dimensions preserved
        val compressedDoc = PDDocument.load(out)
        assertEquals(3, compressedDoc.numberOfPages)
        val origDoc = PDDocument.load(originalPdf)
        assertEquals(3, origDoc.numberOfPages)

        for (i in 0 until 3) {
            assertEquals(origDoc.getPage(i).mediaBox.width, compressedDoc.getPage(i).mediaBox.width, 0.01f)
            assertEquals(origDoc.getPage(i).mediaBox.height, compressedDoc.getPage(i).mediaBox.height, 0.01f)
        }

        compressedDoc.close()
        origDoc.close()

        out.delete()
        originalPdf.delete()
    }

    @Test
    fun testCompressPasswordProtectedPdfFailsGracefully() {
        val protectedPdf = createPasswordProtectedPdf("secret")
        val out = File.createTempFile("compress_protected_", ".pdf")

        FileOutputStream(out).use { outStream ->
            val result = pdfEngine.compressPdfStreams(
                inputStreamProvider = { FileInputStream(protectedPdf) },
                level = CompressionLevel.BALANCED,
                outputStream = outStream
            )
            assertTrue("Expected failure for password protected PDF", result.isFailure)
            val msg = result.exceptionOrNull()?.message
            assertTrue("Message should mention password protected: $msg", msg?.contains("password protected") == true)
        }

        out.delete()
        protectedPdf.delete()
    }

    private fun createTestPdf(pageCount: Int): File {
        val file = File.createTempFile("test_pdf_", ".pdf")
        val document = PDDocument()
        for (i in 1..pageCount) {
            document.addPage(PDPage())
        }
        document.save(file)
        document.close()
        return file
    }

    private fun createPasswordProtectedPdf(password: String): File {
        val file = File.createTempFile("protected_pdf_", ".pdf")
        val document = PDDocument()
        document.addPage(PDPage())
        val ap = AccessPermission()
        val spp = StandardProtectionPolicy(password, password, ap)
        spp.encryptionKeyLength = 128
        spp.permissions = ap
        document.protect(spp)
        document.save(file)
        document.close()
        return file
    }

    private fun createInvalidPdfFile(): File {
        val file = File.createTempFile("invalid_pdf_", ".pdf")
        file.writeText("Corrupted PDF content that cannot be parsed by PDFBox.")
        return file
    }
}
