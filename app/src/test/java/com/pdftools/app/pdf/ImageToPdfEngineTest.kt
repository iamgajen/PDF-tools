package com.pdftools.app.pdf

import com.pdftools.app.domain.pdf.ImageStreamSpec
import com.pdftools.app.domain.pdf.ImageToPdfPageSize
import com.pdftools.app.domain.pdf.ImageToPdfQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

class ImageToPdfEngineTest {

    private lateinit var pdfEngine: PdfEngine

    @Before
    fun setUp() {
        pdfEngine = PdfEngineImpl()
    }

    @Test
    fun testHandleEmptyImageSelection() {
        val outputFile = File.createTempFile("output_empty_img_", ".pdf")
        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.convertImagesToPdfStreams(
                imageSpecs = emptyList(),
                pageSize = ImageToPdfPageSize.A4,
                quality = ImageToPdfQuality.HIGH,
                outputStream = outStream
            )
            assertTrue("Expected failure for empty specs list", result.isFailure)
            assertEquals("Please select at least one image to create a PDF.", result.exceptionOrNull()?.message)
        }
        outputFile.delete()
    }

    @Test
    fun testHandleCorruptedOrUnreadableImageStream() {
        val corruptedBytes = "This is not a valid image file".toByteArray()
        val outputFile = File.createTempFile("output_corrupted_img_", ".pdf")

        val imageSpecs = listOf(
            ImageStreamSpec(inputStreamProvider = { ByteArrayInputStream(corruptedBytes) }, name = "corrupted.jpg")
        )

        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.convertImagesToPdfStreams(
                imageSpecs = imageSpecs,
                pageSize = ImageToPdfPageSize.A4,
                quality = ImageToPdfQuality.HIGH,
                outputStream = outStream
            )
            assertTrue("Expected failure for corrupted image stream", result.isFailure)
            val msg = result.exceptionOrNull()?.message
            assertTrue("Error message should mention unable to decode: $msg", msg?.contains("Unable to decode image 'corrupted.jpg'") == true)
        }

        outputFile.delete()
    }

    @Test
    fun testHandleNullInputStream() {
        val outputFile = File.createTempFile("output_null_stream_", ".pdf")

        val imageSpecs = listOf(
            ImageStreamSpec(inputStreamProvider = { null }, name = "missing.png")
        )

        FileOutputStream(outputFile).use { outStream ->
            val result = pdfEngine.convertImagesToPdfStreams(
                imageSpecs = imageSpecs,
                pageSize = ImageToPdfPageSize.A4,
                quality = ImageToPdfQuality.HIGH,
                outputStream = outStream
            )
            assertTrue("Expected failure when stream provider returns null", result.isFailure)
            val msg = result.exceptionOrNull()?.message
            assertTrue("Error message should mention unable to decode or access: $msg", msg?.contains("Unable to decode image 'missing.png'") == true || msg?.contains("Unable to access image") == true)
        }

        outputFile.delete()
    }
}
