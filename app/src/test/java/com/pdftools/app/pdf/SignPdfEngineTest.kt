package com.pdftools.app.pdf

import com.pdftools.app.domain.pdf.SignatureSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SignPdfEngineTest {

    private lateinit var pdfEngine: PdfEngine

    @Before
    fun setUp() {
        pdfEngine = PdfEngineImpl()
    }

    @Test
    fun testSignPdfAddsSignatureAndPreservesPages() {
        val originalPdf = createTestPdf(pageCount = 4)
        val outFile = File.createTempFile("signed_pdf_out_", ".pdf")

        val signature1 = SignatureSpec(
            id = "sig1",
            pageIndex = 0,
            normalizedX = 0.2f,
            normalizedY = 0.3f,
            normalizedWidth = 0.4f,
            normalizedHeight = 0.2f
        )

        val signature2 = SignatureSpec(
            id = "sig2",
            pageIndex = 2,
            normalizedX = 0.1f,
            normalizedY = 0.5f,
            normalizedWidth = 0.3f,
            normalizedHeight = 0.15f
        )

        FileOutputStream(outFile).use { outStream ->
            val result = pdfEngine.signPdfStreams(
                inputStreamProvider = { FileInputStream(originalPdf) },
                signatures = listOf(signature1, signature2),
                outputStream = outStream
            )
            assertTrue("Expected signing to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals(4, result.getOrNull())
        }

        // Verify signed PDF output is readable and page count is preserved
        val signedDoc = PDDocument.load(outFile)
        assertEquals(4, signedDoc.numberOfPages)

        // Verify original PDF page count and dimensions match
        val origDoc = PDDocument.load(originalPdf)
        assertEquals(4, origDoc.numberOfPages)
        for (i in 0 until 4) {
            assertEquals(origDoc.getPage(i).mediaBox.width, signedDoc.getPage(i).mediaBox.width, 0.01f)
            assertEquals(origDoc.getPage(i).mediaBox.height, signedDoc.getPage(i).mediaBox.height, 0.01f)
        }

        signedDoc.close()
        origDoc.close()

        outFile.delete()
        originalPdf.delete()
    }

    @Test
    fun testSignPdfEmptySignaturesFails() {
        val originalPdf = createTestPdf(pageCount = 2)
        val outFile = File.createTempFile("signed_empty_out_", ".pdf")

        FileOutputStream(outFile).use { outStream ->
            val result = pdfEngine.signPdfStreams(
                inputStreamProvider = { FileInputStream(originalPdf) },
                signatures = emptyList(),
                outputStream = outStream
            )
            assertTrue("Expected failure when no signatures provided", result.isFailure)
        }

        outFile.delete()
        originalPdf.delete()
    }

    @Test
    fun testSignPasswordProtectedPdfFails() {
        val protectedPdf = createPasswordProtectedPdf("secret")
        val outFile = File.createTempFile("signed_protected_out_", ".pdf")

        val sigSpec = SignatureSpec(
            id = "sig1",
            pageIndex = 0,
            normalizedX = 0.1f,
            normalizedY = 0.1f,
            normalizedWidth = 0.3f,
            normalizedHeight = 0.2f
        )

        FileOutputStream(outFile).use { outStream ->
            val result = pdfEngine.signPdfStreams(
                inputStreamProvider = { FileInputStream(protectedPdf) },
                signatures = listOf(sigSpec),
                outputStream = outStream
            )
            assertTrue("Expected failure for password protected PDF", result.isFailure)
            val msg = result.exceptionOrNull()?.message
            assertTrue(msg?.contains("password protected") == true)
        }

        outFile.delete()
        protectedPdf.delete()
    }

    private fun createTestPdf(pageCount: Int): File {
        val file = File.createTempFile("test_sign_pdf_", ".pdf")
        val document = PDDocument()
        for (i in 1..pageCount) {
            document.addPage(PDPage())
        }
        document.save(file)
        document.close()
        return file
    }

    private fun createPasswordProtectedPdf(password: String): File {
        val file = File.createTempFile("protected_sign_pdf_", ".pdf")
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
}
