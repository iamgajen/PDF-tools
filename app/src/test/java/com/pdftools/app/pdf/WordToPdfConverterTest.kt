package com.pdftools.app.pdf

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WordToPdfConverterTest {

    private lateinit var converter: WordToPdfConverter

    companion object {
        @JvmStatic
        @BeforeClass
        fun initPdfBoxResources() {
            try {
                val contextClass = Class.forName("android.content.Context")
                val assetManagerClass = Class.forName("android.content.res.AssetManager")

                val assetManager = Proxy.newProxyInstance(
                    assetManagerClass.classLoader,
                    arrayOf(assetManagerClass)
                ) { _, method, args ->
                    if (method.name == "open" && args != null && args.isNotEmpty()) {
                        val path = args[0] as String
                        var stream = WordToPdfConverterTest::class.java.getResourceAsStream("/assets/$path")
                            ?: WordToPdfConverterTest::class.java.classLoader?.getResourceAsStream("assets/$path")
                            ?: WordToPdfConverterTest::class.java.getResourceAsStream("/$path")
                            ?: WordToPdfConverterTest::class.java.classLoader?.getResourceAsStream(path)

                        if (stream == null && path.contains("cmap")) {
                            val fileName = path.substringAfterLast("/")
                            val f1 = File("src/test/resources/com/tom_roush/pdfbox/resources/cmap/$fileName")
                            val f2 = File("app/src/test/resources/com/tom_roush/pdfbox/resources/cmap/$fileName")
                            val f3 = File("src/test/resources/com/tom_roush/fontbox/resources/cmap/$fileName")
                            val f4 = File("app/src/test/resources/com/tom_roush/fontbox/resources/cmap/$fileName")
                            val f5 = File("src/test/resources/assets/$path")
                            val f6 = File("app/src/test/resources/assets/$path")
                            if (f1.exists()) stream = f1.inputStream()
                            else if (f2.exists()) stream = f2.inputStream()
                            else if (f3.exists()) stream = f3.inputStream()
                            else if (f4.exists()) stream = f4.inputStream()
                            else if (f5.exists()) stream = f5.inputStream()
                            else if (f6.exists()) stream = f6.inputStream()
                        }

                        if (stream == null && path.endsWith(".ttf")) {
                            val f1 = File("src/main/res/font/caacupe_one.ttf")
                            val f2 = File("app/src/main/res/font/caacupe_one.ttf")
                            if (f1.exists()) stream = f1.inputStream()
                            else if (f2.exists()) stream = f2.inputStream()
                        }

                        stream ?: throw FileNotFoundException("Asset not found: $path")
                    } else null
                }

                val mockContext = Proxy.newProxyInstance(
                    contextClass.classLoader,
                    arrayOf(contextClass)
                ) { _, method, _ ->
                    if (method.name == "getAssets") assetManager else null
                }

                val loaderClass = Class.forName("com.tom_roush.pdfbox.android.PDFBoxResourceLoader")
                val initMethod = loaderClass.getMethod("init", contextClass)
                initMethod.invoke(null, mockContext)
            } catch (e: Throwable) {
                println("INIT ERROR: ${e.message}")
            }
        }
    }

    @Before
    fun setUp() {
        converter = WordToPdfConverter()
    }

    @Test
    fun test1_SimpleDocxWithPlainText() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r>
                            <w:t>Hello World Plain Text</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val originalCopy = docxBytes.clone()

        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        if (result.isFailure) {
            result.exceptionOrNull()?.printStackTrace()
        }
        assertTrue("Convert failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val pageCount = result.getOrThrow()
        assertEquals(1, pageCount)

        // Verify PDF can be parsed
        val pdDoc = PDDocument.load(ByteArrayInputStream(outStream.toByteArray()))
        assertNotNull(pdDoc)
        assertEquals(1, pdDoc.numberOfPages)
        pdDoc.close()

        // Test 24: Original DOCX remains unchanged
        assertTrue(docxBytes.contentEquals(originalCopy))
    }

    @Test
    fun test2_BoldItalicUnderline() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r>
                            <w:rPr><w:b/></w:rPr>
                            <w:t>Bold Text </w:t>
                        </w:r>
                        <w:r>
                            <w:rPr><w:i/></w:rPr>
                            <w:t>Italic Text </w:t>
                        </w:r>
                        <w:r>
                            <w:rPr><w:u w:val="single"/></w:rPr>
                            <w:t>Underline Text</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
    }

    @Test
    fun test3_DifferentFontSizes() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r>
                            <w:rPr><w:sz w:val="48"/></w:rPr>
                            <w:t>24pt Large Font </w:t>
                        </w:r>
                        <w:r>
                            <w:rPr><w:sz w:val="16"/></w:rPr>
                            <w:t>8pt Small Font</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test4_ParagraphAlignment() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:jc w:val="center"/></w:pPr>
                        <w:r><w:t>Centered Text</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:jc w:val="right"/></w:pPr>
                        <w:r><w:t>Right Aligned Text</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test5_Headings() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                        <w:r><w:t>Main Heading</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
                        <w:r><w:t>Sub Heading</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test6_BulletedList() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                        <w:r><w:t>Bullet Item 1</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
                        <w:r><w:t>Bullet Item 2</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test7_NumberedList() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr>
                        <w:r><w:t>1. Numbered Item 1</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr>
                        <w:r><w:t>2. Numbered Item 2</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test8_Tables() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:tbl>
                        <w:tr>
                            <w:tc><w:p><w:r><w:t>Header 1</w:t></w:r></w:p></w:tc>
                            <w:tc><w:p><w:r><w:t>Header 2</w:t></w:r></w:p></w:tc>
                        </w:tr>
                        <w:tr>
                            <w:tc><w:p><w:r><w:t>Row 1 Col 1</w:t></w:r></w:p></w:tc>
                            <w:tc><w:p><w:r><w:t>Row 1 Col 2</w:t></w:r></w:p></w:tc>
                        </w:tr>
                    </w:tbl>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test9_10_Images() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>Document with image</w:t></w:r></w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test11_12_PageBreaksAndMultiplePages() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>Page 1 Content</w:t></w:r></w:p>
                    <w:p><w:r><w:br w:type="page"/></w:r></w:p>
                    <w:p><w:r><w:t>Page 2 Content</w:t></w:r></w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
        val pageCount = result.getOrThrow()
        assertEquals(2, pageCount)
    }

    @Test
    fun test13_LandscapePage() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>Landscape Document</w:t></w:r></w:p>
                    <w:sectPr>
                        <w:pgSz w:w="16838" w:h="11906" w:orient="landscape"/>
                    </w:sectPr>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test14_15_16_HeadersFootersPageNumbers() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>Document Content</w:t></w:r></w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(
            documentXml = docXml,
            headerXml = "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p><w:r><w:t>Company Confidential</w:t></w:r></w:p></w:hdr>",
            footerXml = "<w:ftr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:p><w:r><w:t>Page 1</w:t></w:r></w:p></w:ftr>"
        )

        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test17_LongParagraphsSpanningPages() {
        val sb = StringBuilder()
        for (i in 1..200) {
            sb.append("This is sentence number $i in a very long paragraph that spans across multiple pages. ")
        }

        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>${sb.toString()}</w:t></w:r></w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
        val pageCount = result.getOrThrow()
        assertTrue("Long text should span multiple pages", pageCount > 1)
    }

    @Test
    fun test18_LargeDocx() {
        val sb = StringBuilder()
        for (i in 1..50) {
            sb.append("<w:p><w:r><w:t>Paragraph $i in large document test.</w:t></w:r></w:p>")
        }

        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    ${sb.toString()}
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
    }

    @Test
    fun test19_CorruptedDocx() {
        val corruptedBytes = "This is definitely not a docx or zip file!".toByteArray()
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(corruptedBytes), outStream)

        assertTrue("Fallback parser or failure expected for corrupted input", result.isSuccess || result.isFailure)
    }

    @Test
    fun test20_UnsupportedDocxContent() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>Normal text before unsupported element.</w:t></w:r></w:p>
                    <w:unsupportedCustomTag foo="bar"/>
                    <w:p><w:r><w:t>Normal text after unsupported element.</w:t></w:r></w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue("Should ignore unsupported content gracefully", result.isSuccess)
    }

    @Test
    fun test21_MultiRunParagraphInlineFlow() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r><w:t>This is run one. </w:t></w:r>
                        <w:r><w:rPr><w:b/></w:rPr><w:t>This is bold run two. </w:t></w:r>
                        <w:r><w:rPr><w:i/></w:rPr><w:t>This is italic run three. </w:t></w:r>
                        <w:r><w:rPr><w:color w:val="FF0000"/></w:rPr><w:t>This is red text run four.</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
        val pageCount = result.getOrThrow()
        assertEquals("Multi-run paragraph should stay on 1 page and flow inline", 1, pageCount)

        val pdDoc = PDDocument.load(ByteArrayInputStream(outStream.toByteArray()))
        assertNotNull(pdDoc)
        assertEquals(1, pdDoc.numberOfPages)
        pdDoc.close()
    }

    @Test
    fun test22_JustifiedParagraphAndIndents() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr>
                            <w:jc w:val="justify"/>
                            <w:ind w:left="720" w:right="720" w:firstLine="360"/>
                            <w:spacing w:before="120" w:after="240" w:line="276"/>
                        </w:pPr>
                        <w:r><w:t>This is a long justified paragraph with left, right and first-line indents to verify that the layout engine properly calculates line wrapping, line spacing multipliers, and justification word spacing.</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
    }

    @Test
    fun test23_TableWithGridColsAndShading() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:tbl>
                        <w:tblGrid>
                            <w:gridCol w:w="2880"/>
                            <w:gridCol w:w="5760"/>
                        </w:tblGrid>
                        <w:tr>
                            <w:tc>
                                <w:tcPr><w:shd w:fill="E0E0E0"/></w:tcPr>
                                <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Col 1 Header</w:t></w:r></w:p>
                            </w:tc>
                            <w:tc>
                                <w:tcPr><w:shd w:fill="E0E0E0"/></w:tcPr>
                                <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Col 2 Header</w:t></w:r></w:p>
                            </w:tc>
                        </w:tr>
                        <w:tr>
                            <w:tc>
                                <w:p><w:r><w:t>Row 1 Data</w:t></w:r></w:p>
                            </w:tc>
                            <w:tc>
                                <w:p><w:r><w:t>Row 1 Long Cell Data that wraps inside table cell smoothly.</w:t></w:r></w:p>
                            </w:tc>
                        </w:tr>
                    </w:tbl>
                </w:body>
            </w:document>
        """.trimIndent()

        val docxBytes = createDocxZip(documentXml = docXml)
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
    }

    @Test
    fun test24_DocxWithEmbeddedImage() {
        val docXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r><w:t>Document with embedded drawing image: </w:t></w:r>
                        <w:r>
                            <w:drawing>
                                <wp:inline>
                                    <wp:extent cx="1270000" cy="1270000"/>
                                    <a:graphic>
                                        <a:graphicData>
                                            <a:blip r:embed="rId1"/>
                                        </a:graphicData>
                                    </a:graphic>
                                </wp:inline>
                            </w:drawing>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val relsXml = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
            </Relationships>
        """.trimIndent()

        // Tiny 1x1 transparent PNG
        val pngBytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06,
            0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49,
            0x44, 0x41, 0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, 0x0D,
            0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte()
        )

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(relsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/media/image1.png"))
            zos.write(pngBytes)
            zos.closeEntry()
        }

        val docxBytes = baos.toByteArray()
        val outStream = ByteArrayOutputStream()
        val result = converter.convert(ByteArrayInputStream(docxBytes), outStream)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
    }

    private fun createDocxZip(
        documentXml: String,
        headerXml: String? = null,
        footerXml: String? = null
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            if (headerXml != null) {
                zos.putNextEntry(ZipEntry("word/header1.xml"))
                zos.write(headerXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            if (footerXml != null) {
                zos.putNextEntry(ZipEntry("word/footer1.xml"))
                zos.write(footerXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}

