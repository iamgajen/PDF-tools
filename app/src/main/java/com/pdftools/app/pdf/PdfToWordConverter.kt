package com.pdftools.app.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PdfToWordConverter {

    data class TextRunSpec(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val fontSizePt: Float = 11f,
        val fontFamily: String = "Calibri"
    )

    enum class Alignment { LEFT, CENTER, RIGHT, JUSTIFY }

    sealed interface DocItem

    data class ParagraphItem(
        val runs: List<TextRunSpec>,
        val alignment: Alignment = Alignment.LEFT,
        val spaceBeforePt: Float = 0f,
        val spaceAfterPt: Float = 6f,
        val headingLevel: Int? = null,
        val leftIndentPt: Float = 0f,
        val firstLineIndentPt: Float = 0f
    ) : DocItem

    data class TableCellItem(
        val paragraphs: List<ParagraphItem>,
        val widthPt: Float? = null
    )

    data class TableRowItem(
        val cells: List<TableCellItem>
    )

    data class TableItem(
        val rows: List<TableRowItem>,
        val columnWidthsPt: List<Float> = emptyList()
    ) : DocItem

    data class ImageItem(
        val imageBytes: ByteArray,
        val widthPt: Float,
        val heightPt: Float,
        val format: String = "png",
        val alignment: Alignment = Alignment.CENTER
    ) : DocItem {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ImageItem) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
            if (widthPt != other.widthPt) return false
            if (heightPt != other.heightPt) return false
            if (format != other.format) return false
            if (alignment != other.alignment) return false
            return true
        }

        override fun hashCode(): Int {
            var result = imageBytes.contentHashCode()
            result = 31 * result + widthPt.hashCode()
            result = 31 * result + heightPt.hashCode()
            result = 31 * result + format.hashCode()
            result = 31 * result + alignment.hashCode()
            return result
        }
    }

    object PageBreakItem : DocItem

    data class PageSection(
        val widthPt: Float,
        val heightPt: Float,
        val items: List<DocItem>,
        val isScanned: Boolean = false
    )

    private data class ExtractedChar(
        val unicode: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val fontSize: Float,
        val isBold: Boolean,
        val isItalic: Boolean,
        val fontName: String
    )

    private data class PdfLine(
        val chars: MutableList<ExtractedChar>,
        var y: Float,
        var maxFontSize: Float,
        var minX: Float = 0f,
        var maxX: Float = 0f,
        var width: Float = 0f
    )

    fun convert(
        inputStream: InputStream,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int> {
        return try {
            val document = try {
                PDDocument.load(inputStream)
            } catch (e: Exception) {
                if (e.message?.contains("encrypted", ignoreCase = true) == true ||
                    e.message?.contains("password", ignoreCase = true) == true
                ) {
                    return Result.failure(Exception("This PDF is password protected. Please unlock it first."))
                }
                return Result.failure(Exception("Failed to open PDF document: ${e.message}"))
            }

            document.use { pdDoc ->
                if (pdDoc.isEncrypted) {
                    return Result.failure(Exception("This PDF is password protected. Please unlock it first."))
                }

                val totalPages = pdDoc.numberOfPages
                if (totalPages == 0) {
                    return Result.failure(Exception("PDF file contains no pages."))
                }

                val sections = mutableListOf<PageSection>()

                for (pageIdx in 0 until totalPages) {
                    onProgress?.invoke(pageIdx + 1, totalPages)
                    val page = pdDoc.getPage(pageIdx)
                    val section = processPage(pdDoc, page, pageIdx)
                    sections.add(section)
                }

                generateDocxZip(sections, outputStream)
                Result.success(totalPages)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun processPage(
        document: PDDocument,
        page: PDPage,
        pageIdx: Int
    ): PageSection {
        val cropBox = page.cropBox ?: page.mediaBox
        var pageWidthPt = cropBox.width
        var pageHeightPt = cropBox.height
        val rotation = page.rotation
        if (rotation == 90 || rotation == 270) {
            val temp = pageWidthPt
            pageWidthPt = pageHeightPt
            pageHeightPt = temp
        }

        val chars = extractCharsFromPage(document, pageIdx)
        val pageImages = extractImagesFromPage(page, pageWidthPt, pageHeightPt)

        val nonWhitespaceChars = chars.filter { it.unicode.trim().isNotEmpty() }
        val isScannedPage = nonWhitespaceChars.size < 10

        val pageItems = mutableListOf<DocItem>()

        if (isScannedPage) {
            if (pageImages.isNotEmpty()) {
                pageItems.addAll(pageImages)
            }
            pageItems.add(
                ParagraphItem(
                    runs = listOf(
                        TextRunSpec(
                            text = "[Scanned page converted as image]",
                            isItalic = true,
                            fontSizePt = 9f
                        )
                    ),
                    alignment = Alignment.CENTER,
                    spaceBeforePt = 6f,
                    spaceAfterPt = 12f
                )
            )
        } else {
            val lines = groupCharsIntoLines(chars)
            val items = reconstructLayoutFromLines(lines, pageWidthPt, pageImages)
            pageItems.addAll(items)
        }

        return PageSection(
            widthPt = pageWidthPt,
            heightPt = pageHeightPt,
            items = pageItems,
            isScanned = isScannedPage
        )
    }

    private fun extractCharsFromPage(
        document: PDDocument,
        pageIdx: Int
    ): List<ExtractedChar> {
        val extracted = mutableListOf<ExtractedChar>()
        try {
            val stripper = object : PDFTextStripper() {
                init {
                    sortByPosition = true
                }

                override fun processTextPosition(text: TextPosition) {
                    val str = text.unicode
                    if (!str.isNullOrEmpty()) {
                        val font = text.font
                        val fontName = font?.name ?: ""
                        val isBold = fontName.contains("Bold", ignoreCase = true) ||
                                fontName.contains("Black", ignoreCase = true) ||
                                fontName.contains("Heavy", ignoreCase = true)
                        val isItalic = fontName.contains("Italic", ignoreCase = true) ||
                                fontName.contains("Oblique", ignoreCase = true)

                        extracted.add(
                            ExtractedChar(
                                unicode = str,
                                x = text.xDirAdj,
                                y = text.yDirAdj,
                                width = text.widthDirAdj,
                                height = text.heightDir,
                                fontSize = if (text.fontSizeInPt > 0) text.fontSizeInPt else 11f,
                                isBold = isBold,
                                isItalic = isItalic,
                                fontName = fontName
                            )
                        )
                    }
                }
            }

            stripper.startPage = pageIdx + 1
            stripper.endPage = pageIdx + 1
            stripper.writeText(document, StringWriter())
        } catch (_: Throwable) {
        }

        return extracted
    }

    private fun extractImagesFromPage(
        page: PDPage,
        pageWidthPt: Float,
        pageHeightPt: Float
    ): List<ImageItem> {
        val images = mutableListOf<ImageItem>()
        try {
            val resources = page.resources ?: return emptyList()
            for (name in resources.xObjectNames) {
                if (resources.isImageXObject(name)) {
                    val xobject = resources.getXObject(name)
                    if (xobject is PDImageXObject) {
                        val bitmap = xobject.image ?: continue
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                        val bytes = stream.toByteArray()
                        if (bytes.isNotEmpty()) {
                            val maxW = min(pageWidthPt - 72f, 450f)
                            val imgW = min(maxW, bitmap.width.toFloat() * 0.75f)
                            val aspectRatio = bitmap.height.toFloat() / max(1f, bitmap.width.toFloat())
                            val imgH = min(pageHeightPt - 72f, imgW * aspectRatio)

                            images.add(
                                ImageItem(
                                    imageBytes = bytes,
                                    widthPt = imgW,
                                    heightPt = imgH,
                                    format = "png"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return images
    }

    private fun groupCharsIntoLines(chars: List<ExtractedChar>): List<PdfLine> {
        if (chars.isEmpty()) return emptyList()

        val sortedChars = chars.sortedBy { it.y }
        val lines = mutableListOf<PdfLine>()

        for (char in sortedChars) {
            val matchingLine = lines.lastOrNull { line ->
                val threshold = max(line.maxFontSize, char.fontSize) * 0.45f
                abs(line.y - char.y) <= max(threshold, 3.0f)
            }

            if (matchingLine != null) {
                matchingLine.chars.add(char)
                matchingLine.maxFontSize = max(matchingLine.maxFontSize, char.fontSize)
            } else {
                lines.add(
                    PdfLine(
                        chars = mutableListOf(char),
                        y = char.y,
                        maxFontSize = char.fontSize
                    )
                )
            }
        }

        for (line in lines) {
            line.chars.sortBy { it.x }
            if (line.chars.isNotEmpty()) {
                line.minX = line.chars.first().x
                val last = line.chars.last()
                line.maxX = last.x + last.width
                line.width = line.maxX - line.minX
            }
        }

        return lines.sortedBy { it.y }
    }

    private fun reconstructLayoutFromLines(
        lines: List<PdfLine>,
        pageWidthPt: Float,
        pageImages: List<ImageItem>
    ): List<DocItem> {
        if (lines.isEmpty()) return pageImages

        val allFontSizes = lines.map { it.maxFontSize }.sorted()
        val bodyFontSize = if (allFontSizes.isNotEmpty()) {
            allFontSizes[allFontSizes.size / 2]
        } else 11f

        val items = mutableListOf<DocItem>()
        var i = 0

        while (i < lines.size) {
            val tableResult = tryDetectTable(lines, i)
            if (tableResult != null) {
                items.add(tableResult.tableItem)
                i = tableResult.nextIndex
                continue
            }

            val paragraphLines = mutableListOf<PdfLine>()
            paragraphLines.add(lines[i])
            i++

            while (i < lines.size) {
                val currentLine = lines[i]
                val prevLine = paragraphLines.last()

                val gapY = currentLine.y - (prevLine.y + prevLine.maxFontSize)
                val fontDiff = abs(currentLine.maxFontSize - prevLine.maxFontSize)

                val isCurrentHeading = isLineHeading(currentLine, bodyFontSize)
                val isPrevHeading = isLineHeading(prevLine, bodyFontSize)

                val contentWidth = pageWidthPt - 108f
                val isPrevShortLine = prevLine.width < contentWidth * 0.72f && (prevLine.maxX < pageWidthPt - 72f)
                val isIndented = currentLine.minX > prevLine.minX + 14f

                val isParagraphBreak = isCurrentHeading ||
                        isPrevHeading ||
                        gapY > max(prevLine.maxFontSize, currentLine.maxFontSize) * 1.5f ||
                        gapY > 16.0f ||
                        fontDiff > 2.0f ||
                        isPrevShortLine ||
                        isIndented

                if (isParagraphBreak) {
                    break
                } else {
                    paragraphLines.add(currentLine)
                    i++
                }
            }

            val pItem = createParagraphFromLines(paragraphLines, pageWidthPt)
            items.add(pItem)
        }

        if (pageImages.isNotEmpty()) {
            items.addAll(pageImages)
        }

        return items
    }

    private data class TableDetectResult(
        val tableItem: TableItem,
        val nextIndex: Int
    )

    private fun tryDetectTable(
        lines: List<PdfLine>,
        startIndex: Int
    ): TableDetectResult? {
        if (startIndex >= lines.size - 1) return null

        val candidateRows = mutableListOf<List<PdfLine>>()

        var currIdx = startIndex
        while (currIdx < lines.size) {
            val line = lines[currIdx]
            val segments = splitLineIntoSegments(line)
            if (segments.size >= 2) {
                candidateRows.add(listOf(line))
                currIdx++
            } else {
                break
            }
        }

        if (candidateRows.size < 2) return null

        val tableRows = mutableListOf<TableRowItem>()
        for (rowLines in candidateRows) {
            val line = rowLines.first()
            val segments = splitLineIntoSegments(line)
            val cells = segments.map { segChars ->
                val runs = buildRunsFromChars(segChars)
                TableCellItem(
                    paragraphs = listOf(
                        ParagraphItem(
                            runs = runs,
                            alignment = Alignment.LEFT,
                            spaceBeforePt = 2f,
                            spaceAfterPt = 2f
                        )
                    )
                )
            }
            tableRows.add(TableRowItem(cells = cells))
        }

        return TableDetectResult(
            tableItem = TableItem(rows = tableRows),
            nextIndex = startIndex + candidateRows.size
        )
    }

    private fun splitLineIntoSegments(line: PdfLine): List<List<ExtractedChar>> {
        val segments = mutableListOf<List<ExtractedChar>>()
        var currentSegment = mutableListOf<ExtractedChar>()

        for (char in line.chars) {
            if (currentSegment.isEmpty()) {
                currentSegment.add(char)
            } else {
                val prev = currentSegment.last()
                val gap = char.x - (prev.x + prev.width)
                if (gap > 24f) {
                    segments.add(currentSegment)
                    currentSegment = mutableListOf(char)
                } else {
                    currentSegment.add(char)
                }
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment)
        }

        return segments
    }

    private fun isLineHeading(line: PdfLine, bodyFontSize: Float): Boolean {
        val lineText = line.chars.joinToString("") { it.unicode }.trim()
        if (lineText.length > 70) return false
        if (line.maxFontSize >= 14f || line.maxFontSize >= bodyFontSize + 2.5f) return true
        val isAllBold = line.chars.all { it.isBold }
        return isAllBold && lineText.length < 50
    }

    private fun createParagraphFromLines(
        lines: List<PdfLine>,
        pageWidthPt: Float
    ): ParagraphItem {
        val runs = mutableListOf<TextRunSpec>()

        for ((idx, line) in lines.withIndex()) {
            val lineRuns = buildRunsFromChars(line.chars)
            if (idx > 0 && runs.isNotEmpty() && lineRuns.isNotEmpty()) {
                val lastRunText = runs.last().text
                if (!lastRunText.endsWith(" ") && !lastRunText.endsWith("-")) {
                    val firstLineRun = lineRuns.first()
                    lineRuns[0] = firstLineRun.copy(text = " " + firstLineRun.text)
                } else if (lastRunText.endsWith("-")) {
                    val trimmedText = lastRunText.dropLast(1)
                    runs[runs.size - 1] = runs.last().copy(text = trimmedText)
                }
            }
            runs.addAll(lineRuns)
        }

        val firstLine = lines.first()
        val avgFontSize = lines.map { it.maxFontSize }.average().toFloat()

        val headingLevel = when {
            avgFontSize >= 18f -> 1
            avgFontSize >= 15f -> 2
            avgFontSize >= 13f && firstLine.chars.any { it.isBold } -> 3
            else -> null
        }

        val pageCenter = pageWidthPt / 2f
        val lineCenter = (firstLine.minX + firstLine.maxX) / 2f
        val alignment = when {
            abs(lineCenter - pageCenter) < 18f -> Alignment.CENTER
            firstLine.maxX > pageWidthPt - 60f && firstLine.minX > 120f -> Alignment.RIGHT
            else -> Alignment.LEFT
        }

        return ParagraphItem(
            runs = runs,
            alignment = alignment,
            spaceBeforePt = if (headingLevel != null) 12f else 0f,
            spaceAfterPt = if (headingLevel != null) 6f else 4f,
            headingLevel = headingLevel
        )
    }

    private fun buildRunsFromChars(chars: List<ExtractedChar>): MutableList<TextRunSpec> {
        val runs = mutableListOf<TextRunSpec>()
        if (chars.isEmpty()) return runs

        var currentText = StringBuilder()
        var currentBold = chars.first().isBold
        var currentItalic = chars.first().isItalic
        var currentFontSize = chars.first().fontSize

        for (i in chars.indices) {
            val char = chars[i]
            if (i > 0) {
                val prev = chars[i - 1]
                val gap = char.x - (prev.x + prev.width)
                val avgW = max(1f, (prev.width + char.width) / 2f)

                if (gap > avgW * 0.35f && !currentText.endsWith(" ")) {
                    currentText.append(" ")
                }
            }

            if (char.isBold != currentBold || char.isItalic != currentItalic || abs(char.fontSize - currentFontSize) > 0.5f) {
                if (currentText.isNotEmpty()) {
                    runs.add(
                        TextRunSpec(
                            text = currentText.toString(),
                            isBold = currentBold,
                            isItalic = currentItalic,
                            fontSizePt = currentFontSize
                        )
                    )
                }
                currentText = StringBuilder()
                currentBold = char.isBold
                currentItalic = char.isItalic
                currentFontSize = char.fontSize
            }

            currentText.append(char.unicode)
        }

        if (currentText.isNotEmpty()) {
            runs.add(
                TextRunSpec(
                    text = currentText.toString(),
                    isBold = currentBold,
                    isItalic = currentItalic,
                    fontSizePt = currentFontSize
                )
            )
        }

        return runs
    }

    private fun generateDocxZip(sections: List<PageSection>, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(getContentTypesXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(getRelsXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. word/styles.xml
            zos.putNextEntry(ZipEntry("word/styles.xml"))
            zos.write(getStylesXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Collect all images across sections
            val imageEntries = mutableListOf<Pair<String, ByteArray>>() // id -> bytes
            var imageCounter = 1

            val imageRelMap = mutableMapOf<ImageItem, String>() // item -> rId

            for (section in sections) {
                for (item in section.items) {
                    if (item is ImageItem) {
                        val relId = "rId${imageCounter + 10}"
                        val imgFileName = "image$imageCounter.png"
                        imageCounter++

                        zos.putNextEntry(ZipEntry("word/media/$imgFileName"))
                        zos.write(item.imageBytes)
                        zos.closeEntry()

                        imageEntries.add(Pair(relId, item.imageBytes))
                        imageRelMap[item] = relId
                    }
                }
            }

            // 4. word/_rels/document.xml.rels
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(getDocumentRelsXml(imageEntries).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(getDocumentXml(sections, imageRelMap).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun getContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""
    }

    private fun getRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
    }

    private fun getStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
        <w:sz w:val="22"/>
        <w:szCs w:val="22"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="32"/>
      <w:szCs w:val="32"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="28"/>
      <w:szCs w:val="28"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading3">
    <w:name w:val="heading 3"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:rPr>
      <w:b/>
      <w:sz w:val="24"/>
      <w:szCs w:val="24"/>
    </w:rPr>
  </w:style>
</w:styles>"""
    }

    private fun getDocumentRelsXml(imageEntries: List<Pair<String, ByteArray>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
""")
        for ((index, entry) in imageEntries.withIndex()) {
            val relId = entry.first
            val imgFileName = "image${index + 1}.png"
            sb.append("""  <Relationship Id="$relId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/$imgFileName"/>
""")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun getDocumentXml(
        sections: List<PageSection>,
        imageRelMap: Map<ImageItem, String>
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>
""")

        for ((secIdx, section) in sections.withIndex()) {
            for (item in section.items) {
                when (item) {
                    is ParagraphItem -> sb.append(renderParagraphXml(item))
                    is TableItem -> sb.append(renderTableXml(item))
                    is ImageItem -> {
                        val relId = imageRelMap[item] ?: "rId10"
                        sb.append(renderImageXml(item, relId))
                    }
                    is PageBreakItem -> {
                        sb.append("""    <w:p><w:r><w:br w:type="page"/></w:r></w:p>
""")
                    }
                }
            }

            if (secIdx < sections.size - 1) {
                sb.append("""    <w:p><w:r><w:br w:type="page"/></w:r></w:p>
""")
            }
        }

        val lastSection = sections.lastOrNull()
        val widthTwips = ((lastSection?.widthPt ?: 595.27f) * 20).toInt()
        val heightTwips = ((lastSection?.heightPt ?: 841.89f) * 20).toInt()

        sb.append("""    <w:sectPr>
      <w:pgSz w:w="$widthTwips" w:h="$heightTwips" w:orient="${if (widthTwips > heightTwips) "landscape" else "portrait"}"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
    </w:sectPr>
  </w:body>
</w:document>""")

        return sb.toString()
    }

    private fun renderParagraphXml(item: ParagraphItem): String {
        val sb = StringBuilder()
        sb.append("    <w:p>\n      <w:pPr>\n")

        when (item.alignment) {
            Alignment.CENTER -> sb.append("        <w:jc w:val=\"center\"/>\n")
            Alignment.RIGHT -> sb.append("        <w:jc w:val=\"right\"/>\n")
            Alignment.JUSTIFY -> sb.append("        <w:jc w:val=\"both\"/>\n")
            Alignment.LEFT -> sb.append("        <w:jc w:val=\"left\"/>\n")
        }

        if (item.headingLevel != null) {
            sb.append("        <w:pStyle w:val=\"Heading${item.headingLevel}\"/>\n")
        }

        val spaceBefore = (item.spaceBeforePt * 20).toInt()
        val spaceAfter = (item.spaceAfterPt * 20).toInt()
        sb.append("        <w:spacing w:before=\"$spaceBefore\" w:after=\"$spaceAfter\"/>\n")

        sb.append("      </w:pPr>\n")

        for (run in item.runs) {
            if (run.text.isEmpty()) continue
            sb.append("      <w:r>\n        <w:rPr>\n")
            if (run.isBold) sb.append("          <w:b/>\n")
            if (run.isItalic) sb.append("          <w:i/>\n")
            val fontHalfPts = (run.fontSizePt * 2).toInt()
            sb.append("          <w:sz w:val=\"$fontHalfPts\"/>\n")
            sb.append("          <w:szCs w:val=\"$fontHalfPts\"/>\n")
            sb.append("        </w:rPr>\n")
            sb.append("        <w:t xml:space=\"preserve\">${run.text.xmlEscape()}</w:t>\n")
            sb.append("      </w:r>\n")
        }

        sb.append("    </w:p>\n")
        return sb.toString()
    }

    private fun renderTableXml(table: TableItem): String {
        val sb = StringBuilder()
        sb.append("""    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="0" w:type="auto"/>
        <w:tblBorders>
          <w:top w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:left w:val="none"/>
          <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
          <w:right w:val="none"/>
          <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>
          <w:insideV w:val="none"/>
        </w:tblBorders>
      </w:tblPr>
""")

        for (row in table.rows) {
            sb.append("      <w:tr>\n")
            for (cell in row.cells) {
                sb.append("        <w:tc>\n          <w:tcPr>\n            <w:tcW w:w=\"0\" w:type=\"auto\"/>\n          </w:tcPr>\n")
                for (p in cell.paragraphs) {
                    sb.append(renderParagraphXml(p))
                }
                sb.append("        </w:tc>\n")
            }
            sb.append("      </w:tr>\n")
        }

        sb.append("    </w:tbl>\n")
        return sb.toString()
    }

    private fun renderImageXml(image: ImageItem, relId: String): String {
        val cxEmu = (image.widthPt * 12700).toLong()
        val cyEmu = (image.heightPt * 12700).toLong()

        val jc = when (image.alignment) {
            Alignment.CENTER -> "center"
            Alignment.RIGHT -> "right"
            else -> "left"
        }

        return """    <w:p>
      <w:pPr>
        <w:jc w:val="$jc"/>
      </w:pPr>
      <w:r>
        <w:drawing>
          <wp:inline distT="0" distB="0" distL="0" distR="0">
            <wp:extent cx="$cxEmu" cy="$cyEmu"/>
            <wp:docPr id="1" name="Picture"/>
            <a:graphic>
              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                <pic:pic>
                  <pic:nvPicPr>
                    <pic:cNvPr id="0" name="Picture"/>
                    <pic:cNvPolicy/>
                  </pic:nvPicPr>
                  <pic:blipFill>
                    <a:blip r:embed="$relId"/>
                    <a:stretch><a:fillRect/></a:stretch>
                  </pic:blipFill>
                  <pic:spPr>
                    <a:xfrm>
                      <a:off x="0" y="0"/>
                      <a:ext cx="$cxEmu" cy="$cyEmu"/>
                    </a:xfrm>
                    <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                  </pic:spPr>
                </pic:pic>
              </a:graphicData>
            </a:graphic>
          </wp:inline>
        </w:drawing>
      </w:r>
    </w:p>
"""
    }

    private fun String.xmlEscape(): String {
        val sb = StringBuilder()
        for (ch in this) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    if (ch.code in 0x20..0xD7FF || ch.code == 0x9 || ch.code == 0xA || ch.code == 0xD || ch.code in 0xE000..0xFFFD) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }
}
