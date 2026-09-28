package com.pdftools.app.pdf

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class PdfToWordConverterTest {

    private class TestContext(private val assetManager: AssetManager) : ContextWrapper(null) {
        override fun getApplicationContext(): Context {
            return this
        }
        override fun getAssets(): AssetManager {
            return assetManager
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun initPdfBoxResources() {
            try {
                val mockAssetManager = Mockito.mock(AssetManager::class.java)
                val streamAnswer = { invocation: InvocationOnMock ->
                    val path = invocation.arguments[0] as String
                    var stream: InputStream? = PdfToWordConverterTest::class.java.classLoader?.getResourceAsStream("assets/$path")
                        ?: PdfToWordConverterTest::class.java.classLoader?.getResourceAsStream(path)

                    if (stream == null) {
                        val f1 = File("src/test/resources/$path")
                        val f2 = File("src/test/resources/assets/$path")
                        val f3 = File("app/src/test/resources/$path")
                        val f4 = File("app/src/test/resources/assets/$path")
                        if (f1.exists()) stream = f1.inputStream()
                        else if (f2.exists()) stream = f2.inputStream()
                        else if (f3.exists()) stream = f3.inputStream()
                        else if (f4.exists()) stream = f4.inputStream()
                    }

                    stream ?: ByteArrayInputStream("space;0020\na;0061\n".toByteArray())
                }

                Mockito.`when`(mockAssetManager.open(anyString())).thenAnswer(streamAnswer)
                Mockito.`when`(mockAssetManager.open(anyString(), anyInt())).thenAnswer(streamAnswer)

                PDFBoxResourceLoader.init(TestContext(mockAssetManager))
            } catch (e: Throwable) {
                println("INIT MOCKITO ERROR: ${e.message}")
            }
        }
    }

    private lateinit var converter: PdfToWordConverter

    @Before
    fun setUp() {
        converter = PdfToWordConverter()
    }

    @Test
    fun testConvertSimplePdfToWord() {
        val pdfBytes = createTestPdf(pageCount = 2)
        val outputStream = ByteArrayOutputStream()

        val result = converter.convert(
            inputStream = ByteArrayInputStream(pdfBytes),
            outputStream = outputStream
        )

        assertTrue("Expected convert to succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals(2, result.getOrThrow())

        val docxBytes = outputStream.toByteArray()
        assertTrue(docxBytes.isNotEmpty())

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.contains("[Content_Types].xml"))
        assertTrue(entries.contains("_rels/.rels"))
        assertTrue(entries.contains("word/styles.xml"))
        assertTrue(entries.contains("word/_rels/document.xml.rels"))
        assertTrue(entries.contains("word/document.xml"))
    }

    @Test
    fun testConvertCorruptedPdfFailsGracefully() {
        val invalidBytes = "This is not a valid PDF file".toByteArray()
        val outputStream = ByteArrayOutputStream()

        val result = converter.convert(
            inputStream = ByteArrayInputStream(invalidBytes),
            outputStream = outputStream
        )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    private fun createTestPdf(pageCount: Int): ByteArray {
        val doc = PDDocument()
        for (i in 1..pageCount) {
            doc.addPage(PDPage())
        }
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }
}
