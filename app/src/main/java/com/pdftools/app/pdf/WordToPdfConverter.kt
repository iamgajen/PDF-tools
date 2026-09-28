package com.pdftools.app.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class WordToPdfConverter {

    private enum class Alignment { LEFT, CENTER, RIGHT, JUSTIFY }

    private sealed interface InlineElement

    private data class TextRun(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isStrikethrough: Boolean = false,
        val fontSizePt: Float = 11f,
        val fontFamily: String = "Helvetica",
        val colorRgb: Int = 0x000000
    ) : InlineElement

    private data class InlineImage(
        val imageBytes: ByteArray,
        val widthPt: Float,
        val heightPt: Float
    ) : InlineElement

    private object LineBreak : InlineElement

    private sealed interface DocElement

    private data class ParagraphElement(
        val inlines: List<InlineElement>,
        val alignment: Alignment = Alignment.LEFT,
        val spaceBeforePt: Float = 0f,
        val spaceAfterPt: Float = 6f,
        val lineSpacingMult: Float = 1.15f,
        val firstLineIndentPt: Float = 0f,
        val leftIndentPt: Float = 0f,
        val rightIndentPt: Float = 0f,
        val headingLevel: Int? = null,
        val listBulletStr: String? = null,
        val listIndentLevel: Int = 0,
        val isPageBreak: Boolean = false
    ) : DocElement

    private data class TableCell(
        val elements: List<DocElement>,
        val widthPt: Float? = null,
        val backgroundColor: Int? = null,
        val colSpan: Int = 1
    )

    private data class TableRow(
        val cells: List<TableCell>
    )

    private data class TableElement(
        val rows: List<TableRow>,
        val columnWidthsPt: List<Float> = emptyList()
    ) : DocElement

    private data class ImageBlockElement(
        val imageBytes: ByteArray,
        val widthPt: Float,
        val heightPt: Float,
        val alignment: Alignment = Alignment.CENTER
    ) : DocElement

    private object PageBreakElement : DocElement

    private data class DocxDocument(
        var pageWidthPt: Float = 595.27f,
        var pageHeightPt: Float = 841.89f,
        var marginTopPt: Float = 72f,
        var marginBottomPt: Float = 72f,
        var marginLeftPt: Float = 72f,
        var marginRightPt: Float = 72f,
        var headerText: String? = null,
        var footerText: String? = null,
        val elements: MutableList<DocElement> = mutableListOf()
    )

    private val fontCache = mutableMapOf<String, PDFont>()

    fun convert(
        inputStream: InputStream,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int> {
        return try {
            val bytes = inputStream.readBytes()
            if (bytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("The provided Word document is empty."))
            }

            val parsedDoc = parseWordDocument(bytes)
                ?: return Result.failure(IllegalArgumentException("Invalid or corrupted Word document."))

            val pdfDocument = PDDocument()
            try {
                val totalPages = renderPdf(parsedDoc, pdfDocument, onProgress)
                pdfDocument.save(outputStream)
                Result.success(totalPages)
            } finally {
                pdfDocument.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseWordDocument(zipBytes: ByteArray): DocxDocument? {
        val zipEntries = mutableMapOf<String, ByteArray>()
        val mediaEntries = mutableMapOf<String, ByteArray>()

        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val bytes = zis.readBytes()
                        zipEntries[entry.name] = bytes
                        val cleanName = entry.name.removePrefix("word/").removePrefix("/")
                        if (cleanName.startsWith("media/") || entry.name.contains("media/")) {
                            mediaEntries[cleanName] = bytes
                            mediaEntries[entry.name] = bytes
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
            return parseFallbackTextDocument(zipBytes)
        }

        val docXmlBytes = zipEntries["word/document.xml"] ?: return parseFallbackTextDocument(zipBytes)

        val docx = DocxDocument()
        val relsMap = parseRels(zipEntries["word/_rels/document.xml.rels"])
        val stylesMap = parseStyles(zipEntries["word/styles.xml"])
        val numMap = parseNumbering(zipEntries["word/numbering.xml"])

        docx.headerText = parseHeaderFooter(zipEntries["word/header1.xml"])
        docx.footerText = parseHeaderFooter(zipEntries["word/footer1.xml"])

        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        val db = dbf.newDocumentBuilder()
        val xmlDoc = db.parse(ByteArrayInputStream(docXmlBytes))

        val bodyNode = xmlDoc.getElementsByTagNameNS("*", "body").item(0)
            ?: xmlDoc.getElementsByTagName("w:body").item(0)
            ?: return null

        val children = bodyNode.childNodes
        val listCounterMap = mutableMapOf<String, Int>()

        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val elem = node as Element
            when (elem.localName ?: elem.tagName.removePrefix("w:")) {
                "p" -> {
                    val p = parseParagraph(elem, stylesMap, numMap, relsMap, mediaEntries, listCounterMap)
                    if (p != null) docx.elements.add(p)
                }
                "tbl" -> {
                    val tbl = parseTable(elem, stylesMap, numMap, relsMap, mediaEntries, listCounterMap)
                    if (tbl != null) docx.elements.add(tbl)
                }
                "sectPr" -> {
                    parseSectionProperties(elem, docx)
                }
            }
        }

        if (docx.elements.isEmpty()) {
            docx.elements.add(
                ParagraphElement(
                    inlines = listOf(TextRun(text = "Document contains no readable text."))
                )
            )
        }

        return docx
    }

    private fun parseFallbackTextDocument(bytes: ByteArray): DocxDocument? {
        return try {
            val text = String(bytes, Charsets.UTF_8).filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?-;:'\"()/\\" }
            if (text.isBlank()) return null
            val docx = DocxDocument()
            val lines = text.lines()
            for (line in lines) {
                if (line.isNotBlank()) {
                    docx.elements.add(
                        ParagraphElement(inlines = listOf(TextRun(text = line.trim())))
                    )
                }
            }
            if (docx.elements.isEmpty()) null else docx
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRels(relsBytes: ByteArray?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (relsBytes == null) return map
        try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = true
            val xmlDoc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(relsBytes))
            val relNodes = xmlDoc.getElementsByTagNameNS("*", "Relationship")
            for (i in 0 until relNodes.length) {
                val elem = relNodes.item(i) as Element
                val id = elem.getAttribute("Id")
                val target = elem.getAttribute("Target")
                if (id.isNotBlank() && target.isNotBlank()) {
                    map[id] = target
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private data class StyleInfo(
        val headingLevel: Int? = null
    )

    private fun parseStyles(stylesBytes: ByteArray?): Map<String, StyleInfo> {
        val map = mutableMapOf<String, StyleInfo>()
        if (stylesBytes == null) return map
        try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = true
            val xmlDoc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(stylesBytes))
            val styleNodes = xmlDoc.getElementsByTagNameNS("*", "style")
            for (i in 0 until styleNodes.length) {
                val elem = styleNodes.item(i) as Element
                val styleId = elem.getAttributeNS("*", "styleId").ifEmpty { elem.getAttribute("w:styleId") }
                val nameElem = elem.getElementsByTagNameNS("*", "name").item(0) as? Element
                val name = nameElem?.getAttributeNS("*", "val")?.ifEmpty { nameElem.getAttribute("w:val") } ?: ""

                var hLevel: Int? = null
                if (name.startsWith("heading ", ignoreCase = true)) {
                    hLevel = name.substring(8).trim().toIntOrNull()
                } else if (styleId.startsWith("Heading", ignoreCase = true)) {
                    hLevel = styleId.substring(7).trim().toIntOrNull()
                }

                map[styleId] = StyleInfo(headingLevel = hLevel)
            }
        } catch (_: Exception) {}
        return map
    }

    private data class NumberingFormat(
        val numFmt: String,
        val lvlText: String
    )

    private fun parseNumbering(numBytes: ByteArray?): Map<String, Map<Int, NumberingFormat>> {
        val resultMap = mutableMapOf<String, MutableMap<Int, NumberingFormat>>()
        if (numBytes == null) return resultMap
        try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = true
            val xmlDoc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(numBytes))

            val abstractNumMap = mutableMapOf<String, MutableMap<Int, NumberingFormat>>()
            val absNodes = xmlDoc.getElementsByTagNameNS("*", "abstractNum")
            for (i in 0 until absNodes.length) {
                val absElem = absNodes.item(i) as Element
                val absId = absElem.getAttributeNS("*", "abstractNumId").ifEmpty { absElem.getAttribute("w:abstractNumId") }
                val lvlMap = mutableMapOf<Int, NumberingFormat>()

                val lvlNodes = absElem.getElementsByTagNameNS("*", "lvl")
                for (j in 0 until lvlNodes.length) {
                    val lvlElem = lvlNodes.item(j) as Element
                    val ilvl = lvlElem.getAttributeNS("*", "ilvl").ifEmpty { lvlElem.getAttribute("w:ilvl") }.toIntOrNull() ?: 0

                    val numFmtElem = getChildElement(lvlElem, "numFmt")
                    val numFmt = numFmtElem?.getAttributeNS("*", "val")?.ifEmpty { numFmtElem.getAttribute("w:val") } ?: "bullet"

                    val lvlTextElem = getChildElement(lvlElem, "lvlText")
                    val lvlText = lvlTextElem?.getAttributeNS("*", "val")?.ifEmpty { lvlTextElem.getAttribute("w:val") } ?: "•"

                    lvlMap[ilvl] = NumberingFormat(numFmt, lvlText)
                }
                abstractNumMap[absId] = lvlMap
            }

            val numNodes = xmlDoc.getElementsByTagNameNS("*", "num")
            for (i in 0 until numNodes.length) {
                val numElem = numNodes.item(i) as Element
                val numId = numElem.getAttributeNS("*", "numId").ifEmpty { numElem.getAttribute("w:numId") }
                val absRefElem = getChildElement(numElem, "abstractNumId")
                val absId = absRefElem?.getAttributeNS("*", "val")?.ifEmpty { absRefElem.getAttribute("w:val") } ?: ""

                val levels = abstractNumMap[absId]
                if (levels != null) {
                    resultMap[numId] = levels
                }
            }
        } catch (_: Exception) {}
        return resultMap
    }

    private fun parseHeaderFooter(xmlBytes: ByteArray?): String? {
        if (xmlBytes == null) return null
        return try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = true
            val xmlDoc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            val pNodes = xmlDoc.getElementsByTagNameNS("*", "p")
            val sb = StringBuilder()
            for (i in 0 until pNodes.length) {
                val text = (pNodes.item(i) as Element).textContent
                if (!text.isNullOrBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(text.trim())
                }
            }
            if (sb.isEmpty()) null else sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSectionProperties(elem: Element, docx: DocxDocument) {
        val children = elem.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val e = child as Element
            when (e.localName ?: e.tagName.removePrefix("w:")) {
                "pgSz" -> {
                    val wTwips = e.getAttributeNS("*", "w").ifEmpty { e.getAttribute("w:w") }.toFloatOrNull() ?: 11906f
                    val hTwips = e.getAttributeNS("*", "h").ifEmpty { e.getAttribute("w:h") }.toFloatOrNull() ?: 16838f
                    val orient = e.getAttributeNS("*", "orient").ifEmpty { e.getAttribute("w:orient") }

                    var wPt = wTwips / 20f
                    var hPt = hTwips / 20f
                    if (orient.equals("landscape", ignoreCase = true)) {
                        if (wPt < hPt) {
                            val temp = wPt
                            wPt = hPt
                            hPt = temp
                        }
                    }
                    docx.pageWidthPt = wPt
                    docx.pageHeightPt = hPt
                }
                "pgMar" -> {
                    val topTwips = e.getAttributeNS("*", "top").ifEmpty { e.getAttribute("w:top") }.toFloatOrNull() ?: 1440f
                    val botTwips = e.getAttributeNS("*", "bottom").ifEmpty { e.getAttribute("w:bottom") }.toFloatOrNull() ?: 1440f
                    val leftTwips = e.getAttributeNS("*", "left").ifEmpty { e.getAttribute("w:left") }.toFloatOrNull() ?: 1440f
                    val rightTwips = e.getAttributeNS("*", "right").ifEmpty { e.getAttribute("w:right") }.toFloatOrNull() ?: 1440f

                    docx.marginTopPt = topTwips / 20f
                    docx.marginBottomPt = botTwips / 20f
                    docx.marginLeftPt = leftTwips / 20f
                    docx.marginRightPt = rightTwips / 20f
                }
            }
        }
    }

    private fun parseParagraph(
        pElem: Element,
        stylesMap: Map<String, StyleInfo>,
        numMap: Map<String, Map<Int, NumberingFormat>>,
        relsMap: Map<String, String>,
        mediaEntries: Map<String, ByteArray>,
        listCounterMap: MutableMap<String, Int>
    ): ParagraphElement? {
        var alignment = Alignment.LEFT
        var spaceBeforePt = 0f
        var spaceAfterPt = 6f
        var lineSpacingMult = 1.15f
        var firstLineIndentPt = 0f
        var leftIndentPt = 0f
        var rightIndentPt = 0f
        var headingLevel: Int? = null
        var listBulletStr: String? = null
        var listIndentLevel = 0
        var isPageBreak = false

        val pPrNode = getChildElement(pElem, "pPr")
        if (pPrNode != null) {
            val pStyleNode = getChildElement(pPrNode, "pStyle")
            if (pStyleNode != null) {
                val valAttr = pStyleNode.getAttributeNS("*", "val").ifEmpty { pStyleNode.getAttribute("w:val") }
                val styleInfo = stylesMap[valAttr]
                if (styleInfo?.headingLevel != null) {
                    headingLevel = styleInfo.headingLevel
                } else if (valAttr.startsWith("Heading", ignoreCase = true)) {
                    headingLevel = valAttr.substring(7).toIntOrNull() ?: 1
                }
            }

            val jcNode = getChildElement(pPrNode, "jc")
            if (jcNode != null) {
                val alignVal = jcNode.getAttributeNS("*", "val").ifEmpty { jcNode.getAttribute("w:val") }
                alignment = when (alignVal.lowercase()) {
                    "center" -> Alignment.CENTER
                    "right" -> Alignment.RIGHT
                    "both", "justify" -> Alignment.JUSTIFY
                    else -> Alignment.LEFT
                }
            }

            val spacingNode = getChildElement(pPrNode, "spacing")
            if (spacingNode != null) {
                val beforeTwips = spacingNode.getAttributeNS("*", "before").ifEmpty { spacingNode.getAttribute("w:before") }.toFloatOrNull()
                val afterTwips = spacingNode.getAttributeNS("*", "after").ifEmpty { spacingNode.getAttribute("w:after") }.toFloatOrNull()
                val lineVal = spacingNode.getAttributeNS("*", "line").ifEmpty { spacingNode.getAttribute("w:line") }.toFloatOrNull()

                if (beforeTwips != null) spaceBeforePt = beforeTwips / 20f
                if (afterTwips != null) spaceAfterPt = afterTwips / 20f
                if (lineVal != null && lineVal > 0f) {
                    lineSpacingMult = lineVal / 240f
                }
            }

            val indNode = getChildElement(pPrNode, "ind")
            if (indNode != null) {
                val leftTwips = indNode.getAttributeNS("*", "left").ifEmpty { indNode.getAttribute("w:left") }.toFloatOrNull()
                val rightTwips = indNode.getAttributeNS("*", "right").ifEmpty { indNode.getAttribute("w:right") }.toFloatOrNull()
                val firstLineTwips = indNode.getAttributeNS("*", "firstLine").ifEmpty { indNode.getAttribute("w:firstLine") }.toFloatOrNull()
                val hangingTwips = indNode.getAttributeNS("*", "hanging").ifEmpty { indNode.getAttribute("w:hanging") }.toFloatOrNull()

                if (leftTwips != null) leftIndentPt = leftTwips / 20f
                if (rightTwips != null) rightIndentPt = rightTwips / 20f
                if (firstLineTwips != null) firstLineIndentPt = firstLineTwips / 20f
                if (hangingTwips != null) firstLineIndentPt = -hangingTwips / 20f
            }

            val numPrNode = getChildElement(pPrNode, "numPr")
            if (numPrNode != null) {
                val ilvlNode = getChildElement(numPrNode, "ilvl")
                val numIdNode = getChildElement(numPrNode, "numId")
                listIndentLevel = ilvlNode?.getAttributeNS("*", "val")?.ifEmpty { ilvlNode.getAttribute("w:ilvl") }?.toIntOrNull() ?: 0
                val numId = numIdNode?.getAttributeNS("*", "val")?.ifEmpty { numIdNode.getAttribute("w:numId") } ?: ""

                val levels = numMap[numId]
                val levelFmt = levels?.get(listIndentLevel)

                if (levelFmt != null) {
                    if (levelFmt.numFmt == "bullet") {
                        listBulletStr = when (listIndentLevel) {
                            0 -> "•"
                            1 -> "◦"
                            2 -> "▪"
                            else -> "•"
                        }
                    } else {
                        val key = "$numId-$listIndentLevel"
                        val count = (listCounterMap[key] ?: 0) + 1
                        listCounterMap[key] = count
                        listBulletStr = levelFmt.lvlText.replace("%1", count.toString()).replace("%${listIndentLevel + 1}", count.toString())
                    }
                } else {
                    listBulletStr = "•"
                }
            }

            if (getChildElement(pPrNode, "pageBreakBefore") != null) {
                isPageBreak = true
            }
        }

        if (headingLevel != null) {
            spaceBeforePt = maxOf(spaceBeforePt, 12f - headingLevel * 1.5f)
            spaceAfterPt = maxOf(spaceAfterPt, 6f)
        }

        val inlines = mutableListOf<InlineElement>()
        val children = pElem.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val elem = child as Element
            val localName = elem.localName ?: elem.tagName.removePrefix("w:")
            when (localName) {
                "r" -> {
                    val brNode = getChildElement(elem, "br")
                    if (brNode != null) {
                        val brType = brNode.getAttributeNS("*", "type").ifEmpty { brNode.getAttribute("w:type") }
                        if (brType.equals("page", ignoreCase = true)) {
                            isPageBreak = true
                        } else {
                            inlines.add(LineBreak)
                        }
                    }

                    val drawingElem = getChildElement(elem, "drawing") ?: getChildElement(elem, "pict")
                    if (drawingElem != null) {
                        val inlineImg = parseImageElement(drawingElem, relsMap, mediaEntries)
                        if (inlineImg != null) {
                            inlines.add(inlineImg)
                        }
                    }

                    val run = parseRun(elem, headingLevel)
                    if (run != null) {
                        inlines.add(run)
                    }
                }
                "drawing", "pict" -> {
                    val inlineImg = parseImageElement(elem, relsMap, mediaEntries)
                    if (inlineImg != null) {
                        inlines.add(inlineImg)
                    }
                }
            }
        }

        if (inlines.isEmpty() && !isPageBreak) {
            return null
        }

        return ParagraphElement(
            inlines = inlines,
            alignment = alignment,
            spaceBeforePt = spaceBeforePt,
            spaceAfterPt = spaceAfterPt,
            lineSpacingMult = lineSpacingMult,
            firstLineIndentPt = firstLineIndentPt,
            leftIndentPt = leftIndentPt,
            rightIndentPt = rightIndentPt,
            headingLevel = headingLevel,
            listBulletStr = listBulletStr,
            listIndentLevel = listIndentLevel,
            isPageBreak = isPageBreak
        )
    }

    private fun parseImageElement(
        drawingElem: Element,
        relsMap: Map<String, String>,
        mediaEntries: Map<String, ByteArray>
    ): InlineImage? {
        try {
            val blipNodes = drawingElem.getElementsByTagNameNS("*", "blip")
            var embedId = ""
            if (blipNodes.length > 0) {
                val blipElem = blipNodes.item(0) as Element
                embedId = blipElem.getAttributeNS("*", "embed").ifEmpty { blipElem.getAttribute("r:embed") }
            }

            if (embedId.isEmpty()) {
                val imgDataNodes = drawingElem.getElementsByTagNameNS("*", "imagedata")
                if (imgDataNodes.length > 0) {
                    val imgDataElem = imgDataNodes.item(0) as Element
                    embedId = imgDataElem.getAttributeNS("*", "id").ifEmpty { imgDataElem.getAttribute("r:id") }
                }
            }

            if (embedId.isEmpty()) return null
            val targetPath = relsMap[embedId] ?: return null
            val cleanTarget = targetPath.removePrefix("word/").removePrefix("/")
            val imageBytes = mediaEntries[cleanTarget]
                ?: mediaEntries["media/" + cleanTarget.substringAfterLast("/")]
                ?: mediaEntries[targetPath]
                ?: return null

            var widthPt = 200f
            var heightPt = 150f

            val extentNodes = drawingElem.getElementsByTagNameNS("*", "extent")
            if (extentNodes.length > 0) {
                val extentElem = extentNodes.item(0) as Element
                val cx = extentElem.getAttribute("cx").toFloatOrNull() ?: 0f
                val cy = extentElem.getAttribute("cy").toFloatOrNull() ?: 0f
                if (cx > 0f && cy > 0f) {
                    widthPt = cx / 12700f
                    heightPt = cy / 12700f
                }
            }

            if (widthPt <= 0f || heightPt <= 0f) {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    widthPt = bitmap.width * 0.75f
                    heightPt = bitmap.height * 0.75f
                    bitmap.recycle()
                }
            }

            return InlineImage(imageBytes = imageBytes, widthPt = widthPt, heightPt = heightPt)
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseRun(rElem: Element, headingLevel: Int?): TextRun? {
        var isBold = headingLevel != null
        var isItalic = false
        var isUnderline = false
        var isStrikethrough = false
        var fontSizePt = when (headingLevel) {
            1 -> 18f
            2 -> 15f
            3 -> 13f
            4 -> 12f
            else -> 11f
        }
        var fontFamily = "Helvetica"
        var colorRgb = if (headingLevel != null) 0x1A237E else 0x000000

        val rPrNode = getChildElement(rElem, "rPr")
        if (rPrNode != null) {
            if (getChildElement(rPrNode, "b") != null) isBold = true
            if (getChildElement(rPrNode, "i") != null) isItalic = true
            if (getChildElement(rPrNode, "u") != null) isUnderline = true
            if (getChildElement(rPrNode, "strike") != null) isStrikethrough = true

            val szNode = getChildElement(rPrNode, "sz")
            if (szNode != null) {
                val halfPts = szNode.getAttributeNS("*", "val").ifEmpty { szNode.getAttribute("w:val") }.toFloatOrNull()
                if (halfPts != null) fontSizePt = halfPts / 2f
            }

            val colorNode = getChildElement(rPrNode, "color")
            if (colorNode != null) {
                val hexStr = colorNode.getAttributeNS("*", "val").ifEmpty { colorNode.getAttribute("w:val") }
                if (hexStr.length == 6) {
                    val parsedInt = hexStr.toIntOrNull(16)
                    if (parsedInt != null) colorRgb = parsedInt
                }
            }

            val rFontsNode = getChildElement(rPrNode, "rFonts")
            if (rFontsNode != null) {
                val ascii = rFontsNode.getAttributeNS("*", "ascii").ifEmpty { rFontsNode.getAttribute("w:ascii") }
                if (ascii.isNotBlank()) fontFamily = ascii
            }
        }

        val tNode = getChildElement(rElem, "t")
        val text = tNode?.textContent ?: ""
        if (text.isEmpty()) return null

        return TextRun(
            text = text,
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            isStrikethrough = isStrikethrough,
            fontSizePt = fontSizePt,
            fontFamily = fontFamily,
            colorRgb = colorRgb
        )
    }

    private fun parseTable(
        tblElem: Element,
        stylesMap: Map<String, StyleInfo>,
        numMap: Map<String, Map<Int, NumberingFormat>>,
        relsMap: Map<String, String>,
        mediaEntries: Map<String, ByteArray>,
        listCounterMap: MutableMap<String, Int>
    ): TableElement? {
        val columnWidths = mutableListOf<Float>()
        val tblGridNode = getChildElement(tblElem, "tblGrid")
        if (tblGridNode != null) {
            val gridColNodes = tblGridNode.getElementsByTagNameNS("*", "gridCol")
            for (i in 0 until gridColNodes.length) {
                val colElem = gridColNodes.item(i) as Element
                val wTwips = colElem.getAttributeNS("*", "w").ifEmpty { colElem.getAttribute("w:w") }.toFloatOrNull() ?: 0f
                if (wTwips > 0f) {
                    columnWidths.add(wTwips / 20f)
                }
            }
        }

        val rows = mutableListOf<TableRow>()
        val trNodes = tblElem.getElementsByTagNameNS("*", "tr")
        for (i in 0 until trNodes.length) {
            val trElem = trNodes.item(i) as Element
            val cells = mutableListOf<TableCell>()
            val tcNodes = trElem.getElementsByTagNameNS("*", "tc")

            for (j in 0 until tcNodes.length) {
                val tcElem = tcNodes.item(j) as Element
                val elements = mutableListOf<DocElement>()

                val children = tcElem.childNodes
                for (k in 0 until children.length) {
                    val child = children.item(k)
                    if (child.nodeType != Node.ELEMENT_NODE) continue
                    val e = child as Element
                    val localName = e.localName ?: e.tagName.removePrefix("w:")
                    if (localName == "p") {
                        val p = parseParagraph(e, stylesMap, numMap, relsMap, mediaEntries, listCounterMap)
                        if (p != null) elements.add(p)
                    }
                }

                var bgColor: Int? = null
                var cellWidthPt: Float? = null
                var colSpan = 1

                val tcPrNode = getChildElement(tcElem, "tcPr")
                if (tcPrNode != null) {
                    val shdNode = getChildElement(tcPrNode, "shd")
                    if (shdNode != null) {
                        val fillHex = shdNode.getAttributeNS("*", "fill").ifEmpty { shdNode.getAttribute("w:fill") }
                        if (fillHex.length == 6) {
                            bgColor = fillHex.toIntOrNull(16)
                        }
                    }

                    val tcWNode = getChildElement(tcPrNode, "tcW")
                    if (tcWNode != null) {
                        val wTwips = tcWNode.getAttributeNS("*", "w").ifEmpty { tcWNode.getAttribute("w:w") }.toFloatOrNull() ?: 0f
                        if (wTwips > 0f) cellWidthPt = wTwips / 20f
                    }

                    val gridSpanNode = getChildElement(tcPrNode, "gridSpan")
                    if (gridSpanNode != null) {
                        val spanVal = gridSpanNode.getAttributeNS("*", "val").ifEmpty { gridSpanNode.getAttribute("w:val") }.toIntOrNull() ?: 1
                        if (spanVal > 1) colSpan = spanVal
                    }
                }

                cells.add(
                    TableCell(
                        elements = elements,
                        widthPt = cellWidthPt,
                        backgroundColor = bgColor,
                        colSpan = colSpan
                    )
                )
            }
            if (cells.isNotEmpty()) {
                rows.add(TableRow(cells))
            }
        }

        if (rows.isEmpty()) return null
        return TableElement(rows = rows, columnWidthsPt = columnWidths)
    }

    private fun getChildElement(parent: Element, name: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val elem = child as Element
                val localName = elem.localName ?: elem.tagName.removePrefix("w:")
                if (localName == name) return elem
            }
        }
        return null
    }

    private fun selectFont(isBold: Boolean, isItalic: Boolean, family: String, document: PDDocument? = null): PDFont {
        val key = "$family-$isBold-$isItalic"
        fontCache[key]?.let { return it }

        val font: PDFont = try {
            val f = family.lowercase()
            when {
                f.contains("times") || f.contains("serif") -> {
                    when {
                        isBold && isItalic -> PDType1Font.TIMES_BOLD_ITALIC
                        isBold -> PDType1Font.TIMES_BOLD
                        isItalic -> PDType1Font.TIMES_ITALIC
                        else -> PDType1Font.TIMES_ROMAN
                    }
                }
                f.contains("courier") || f.contains("mono") || f.contains("code") -> {
                    when {
                        isBold && isItalic -> PDType1Font.COURIER_BOLD_OBLIQUE
                        isBold -> PDType1Font.COURIER_BOLD
                        isItalic -> PDType1Font.COURIER_OBLIQUE
                        else -> PDType1Font.COURIER
                    }
                }
                else -> {
                    when {
                        isBold && isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                        isBold -> PDType1Font.HELVETICA_BOLD
                        isItalic -> PDType1Font.HELVETICA_OBLIQUE
                        else -> PDType1Font.HELVETICA
                    }
                }
            }
        } catch (_: Throwable) {
            createFallbackFont(document)
        }

        fontCache[key] = font
        return font
    }

    private fun createFallbackFont(document: PDDocument?): PDFont {
        fontCache["fallback"]?.let { return it }

        val doc = document ?: PDDocument()
        val stream = WordToPdfConverter::class.java.getResourceAsStream("/font/caacupe_one.ttf")
            ?: WordToPdfConverter::class.java.classLoader?.getResourceAsStream("font/caacupe_one.ttf")
            ?: File("src/main/res/font/caacupe_one.ttf").takeIf { it.exists() }?.inputStream()
            ?: File("app/src/main/res/font/caacupe_one.ttf").takeIf { it.exists() }?.inputStream()
            ?: File("../app/src/main/res/font/caacupe_one.ttf").takeIf { it.exists() }?.inputStream()

        if (stream != null) {
            val font = PDType0Font.load(doc, stream)
            fontCache["fallback"] = font
            return font
        }

        throw IllegalStateException("Unable to load fallback font")
    }

    private fun getStringWidth(font: PDFont, text: String, fontSize: Float): Float {
        if (text.isEmpty()) return 0f
        return try {
            font.getStringWidth(text) / 1000f * fontSize
        } catch (_: Throwable) {
            text.length * fontSize * 0.5f
        }
    }

    private sealed interface InlineChunk {
        val widthPt: Float
    }

    private data class TextChunk(
        val text: String,
        val font: PDFont,
        val fontSize: Float,
        val isBold: Boolean,
        val isItalic: Boolean,
        val isUnderline: Boolean,
        val isStrikethrough: Boolean,
        val colorRgb: Int,
        override val widthPt: Float
    ) : InlineChunk

    private data class SpaceChunk(
        val font: PDFont,
        val fontSize: Float,
        override val widthPt: Float
    ) : InlineChunk

    private data class ImageChunk(
        val bitmap: Bitmap?,
        val imageBytes: ByteArray,
        override val widthPt: Float,
        val heightPt: Float
    ) : InlineChunk

    private object BreakChunk : InlineChunk {
        override val widthPt: Float = 0f
    }

    private data class LineLayout(
        val chunks: List<InlineChunk>,
        val widthPt: Float,
        val heightPt: Float,
        val baselinePt: Float,
        val isFirstLine: Boolean,
        val isLastLine: Boolean
    )

    private fun createInlineChunks(
        inlines: List<InlineElement>,
        pdfDocument: PDDocument
    ): List<InlineChunk> {
        val chunks = mutableListOf<InlineChunk>()

        for (inline in inlines) {
            when (inline) {
                is TextRun -> {
                    val font = selectFont(inline.isBold, inline.isItalic, inline.fontFamily, pdfDocument)
                    val fontSize = inline.fontSizePt
                    val sanitized = sanitizeText(inline.text)

                    var idx = 0
                    while (idx < sanitized.length) {
                        if (sanitized[idx].isWhitespace()) {
                            val spaceStart = idx
                            while (idx < sanitized.length && sanitized[idx].isWhitespace()) {
                                idx++
                            }
                            val spaceStr = sanitized.substring(spaceStart, idx)
                            val spaceWidth = getStringWidth(font, spaceStr, fontSize)
                            chunks.add(SpaceChunk(font = font, fontSize = fontSize, widthPt = spaceWidth))
                        } else {
                            val wordStart = idx
                            while (idx < sanitized.length && !sanitized[idx].isWhitespace()) {
                                idx++
                            }
                            val wordStr = sanitized.substring(wordStart, idx)
                            val wordWidth = getStringWidth(font, wordStr, fontSize)
                            chunks.add(
                                TextChunk(
                                    text = wordStr,
                                    font = font,
                                    fontSize = fontSize,
                                    isBold = inline.isBold,
                                    isItalic = inline.isItalic,
                                    isUnderline = inline.isUnderline,
                                    isStrikethrough = inline.isStrikethrough,
                                    colorRgb = inline.colorRgb,
                                    widthPt = wordWidth
                                )
                            )
                        }
                    }
                }
                is InlineImage -> {
                    val bitmap = try {
                        BitmapFactory.decodeByteArray(inline.imageBytes, 0, inline.imageBytes.size)
                    } catch (_: Throwable) { null }

                    chunks.add(
                        ImageChunk(
                            bitmap = bitmap,
                            imageBytes = inline.imageBytes,
                            widthPt = inline.widthPt,
                            heightPt = inline.heightPt
                        )
                    )
                }
                is LineBreak -> {
                    chunks.add(BreakChunk)
                }
            }
        }

        return chunks
    }

    private fun layoutParagraphLines(
        chunks: List<InlineChunk>,
        availableWidthFirstLine: Float,
        availableWidthOtherLines: Float,
        lineSpacingMult: Float
    ): List<LineLayout> {
        val resultLines = mutableListOf<LineLayout>()
        if (chunks.isEmpty()) return resultLines

        var currentLineChunks = mutableListOf<InlineChunk>()
        var currentLineWidth = 0f
        var isFirstLine = true

        fun maxLineWidth() = if (isFirstLine) availableWidthFirstLine else availableWidthOtherLines

        fun flushLine(isLast: Boolean) {
            if (currentLineChunks.isEmpty()) return

            while (currentLineChunks.isNotEmpty() && currentLineChunks.last() is SpaceChunk) {
                val removed = currentLineChunks.removeAt(currentLineChunks.size - 1)
                currentLineWidth -= removed.widthPt
            }

            if (currentLineChunks.isEmpty()) return

            var maxFontSize = 11f
            var maxChunkHeight = 0f

            for (c in currentLineChunks) {
                when (c) {
                    is TextChunk -> {
                        maxFontSize = maxOf(maxFontSize, c.fontSize)
                    }
                    is SpaceChunk -> {
                        maxFontSize = maxOf(maxFontSize, c.fontSize)
                    }
                    is ImageChunk -> {
                        maxChunkHeight = maxOf(maxChunkHeight, c.heightPt)
                    }
                    else -> {}
                }
            }

            val textLineHeight = maxFontSize * lineSpacingMult
            val totalLineHeight = maxOf(textLineHeight, maxChunkHeight)
            val baseline = maxFontSize * 0.8f

            resultLines.add(
                LineLayout(
                    chunks = currentLineChunks.toList(),
                    widthPt = currentLineWidth,
                    heightPt = totalLineHeight,
                    baselinePt = baseline,
                    isFirstLine = isFirstLine,
                    isLastLine = isLast
                )
            )

            currentLineChunks = mutableListOf()
            currentLineWidth = 0f
            isFirstLine = false
        }

        for (chunk in chunks) {
            if (chunk is BreakChunk) {
                flushLine(isLast = false)
                continue
            }

            if (chunk is SpaceChunk && currentLineChunks.isEmpty()) {
                continue
            }

            val limitWidth = maxLineWidth()

            if (currentLineWidth + chunk.widthPt <= limitWidth || currentLineChunks.isEmpty()) {
                currentLineChunks.add(chunk)
                currentLineWidth += chunk.widthPt
            } else {
                flushLine(isLast = false)

                if (chunk is SpaceChunk) {
                    continue
                }

                if (chunk is TextChunk && chunk.widthPt > limitWidth) {
                    var remaining = chunk.text
                    while (remaining.isNotEmpty()) {
                        var charCount = remaining.length
                        while (charCount > 1 && getStringWidth(chunk.font, remaining.substring(0, charCount), chunk.fontSize) > limitWidth) {
                            charCount--
                        }
                        val subText = remaining.substring(0, charCount)
                        val subWidth = getStringWidth(chunk.font, subText, chunk.fontSize)
                        val subChunk = chunk.copy(text = subText, widthPt = subWidth)

                        currentLineChunks.add(subChunk)
                        currentLineWidth = subWidth
                        remaining = remaining.substring(charCount)

                        if (remaining.isNotEmpty()) {
                            flushLine(isLast = false)
                        }
                    }
                } else {
                    currentLineChunks.add(chunk)
                    currentLineWidth = chunk.widthPt
                }
            }
        }

        flushLine(isLast = true)
        return resultLines
    }

    private fun drawText(
        cs: PDPageContentStream,
        font: PDFont,
        fontSize: Float,
        r: Int,
        g: Int,
        b: Int,
        x: Float,
        y: Float,
        text: String
    ) {
        if (text.isEmpty()) return
        cs.beginText()
        try {
            cs.setFont(font, fontSize)
            cs.setNonStrokingColor(r, g, b)
            cs.newLineAtOffset(x, y)
            cs.showText(text)
        } catch (_: Throwable) {
        } finally {
            try {
                cs.endText()
            } catch (_: Throwable) {}
        }
    }

    private fun renderPdf(
        docx: DocxDocument,
        pdfDocument: PDDocument,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Int {
        var pageNumber = 1
        var page = PDPage(PDRectangle(docx.pageWidthPt, docx.pageHeightPt))
        pdfDocument.addPage(page)

        var stream = PDPageContentStream(pdfDocument, page)

        val printWidth = docx.pageWidthPt - docx.marginLeftPt - docx.marginRightPt
        var currentY = docx.pageHeightPt - docx.marginTopPt

        fun drawHeaderAndFooter(cs: PDPageContentStream, pNum: Int) {
            val font = selectFont(false, false, "Helvetica", pdfDocument)
            if (!docx.headerText.isNullOrBlank()) {
                drawText(
                    cs = cs,
                    font = font,
                    fontSize = 9f,
                    r = 120, g = 120, b = 120,
                    x = docx.marginLeftPt,
                    y = docx.pageHeightPt - docx.marginTopPt / 2f,
                    text = sanitizeText(docx.headerText!!)
                )
            }

            val footerStr = docx.footerText?.let { "${sanitizeText(it)} | Page $pNum" } ?: "Page $pNum"
            drawText(
                cs = cs,
                font = font,
                fontSize = 9f,
                r = 120, g = 120, b = 120,
                x = docx.marginLeftPt,
                y = docx.marginBottomPt / 2f,
                text = footerStr
            )
        }

        drawHeaderAndFooter(stream, pageNumber)

        fun ensureSpace(requiredHeight: Float) {
            if (currentY - requiredHeight < docx.marginBottomPt) {
                try { stream.close() } catch (_: Throwable) {}
                pageNumber++
                page = PDPage(PDRectangle(docx.pageWidthPt, docx.pageHeightPt))
                pdfDocument.addPage(page)
                stream = PDPageContentStream(pdfDocument, page)
                currentY = docx.pageHeightPt - docx.marginTopPt
                drawHeaderAndFooter(stream, pageNumber)
                onProgress?.invoke(pageNumber, pageNumber)
            }
        }

        fun renderParagraph(p: ParagraphElement, containerAvailableWidth: Float = printWidth, containerLeftMargin: Float = docx.marginLeftPt) {
            if (p.isPageBreak) {
                ensureSpace(docx.pageHeightPt)
            }

            currentY -= p.spaceBeforePt

            val listIndent = if (p.listBulletStr != null) maxOf(p.listIndentLevel * 18f + 18f, p.leftIndentPt) else p.leftIndentPt
            val leftIndent = listIndent
            val rightIndent = p.rightIndentPt
            val firstLineIndent = p.firstLineIndentPt

            val availWidthFirstLine = maxOf(10f, containerAvailableWidth - leftIndent - rightIndent - firstLineIndent)
            val availWidthOtherLines = maxOf(10f, containerAvailableWidth - leftIndent - rightIndent)

            val chunks = createInlineChunks(p.inlines, pdfDocument)
            val lineLayouts = layoutParagraphLines(
                chunks = chunks,
                availableWidthFirstLine = availWidthFirstLine,
                availableWidthOtherLines = availWidthOtherLines,
                lineSpacingMult = p.lineSpacingMult
            )

            for (line in lineLayouts) {
                ensureSpace(line.heightPt)

                val lineAvailWidth = if (line.isFirstLine) availWidthFirstLine else availWidthOtherLines
                val lineLeftBase = containerLeftMargin + leftIndent + (if (line.isFirstLine) firstLineIndent else 0f)

                val slack = lineAvailWidth - line.widthPt
                val startX = when (p.alignment) {
                    Alignment.CENTER -> lineLeftBase + maxOf(0f, slack / 2f)
                    Alignment.RIGHT -> lineLeftBase + maxOf(0f, slack)
                    else -> lineLeftBase
                }

                val spaceChunksCount = line.chunks.count { it is SpaceChunk }
                val extraPerSpace = if (p.alignment == Alignment.JUSTIFY && !line.isLastLine && slack > 0f && spaceChunksCount > 0) {
                    slack / spaceChunksCount.toFloat()
                } else 0f

                if (line.isFirstLine && p.listBulletStr != null) {
                    val bulletFont = selectFont(false, false, "Helvetica", pdfDocument)
                    val bulletX = containerLeftMargin + leftIndent - 14f
                    drawText(
                        cs = stream,
                        font = bulletFont,
                        fontSize = 11f,
                        r = 0, g = 0, b = 0,
                        x = bulletX,
                        y = currentY - line.baselinePt,
                        text = p.listBulletStr
                    )
                }

                var chunkX = startX
                val lineBaseY = currentY - line.baselinePt

                for (c in line.chunks) {
                    when (c) {
                        is TextChunk -> {
                            val r = (c.colorRgb shr 16) and 0xFF
                            val g = (c.colorRgb shr 8) and 0xFF
                            val b = c.colorRgb and 0xFF

                            drawText(
                                cs = stream,
                                font = c.font,
                                fontSize = c.fontSize,
                                r = r, g = g, b = b,
                                x = chunkX,
                                y = lineBaseY,
                                text = c.text
                            )

                            if (c.isUnderline) {
                                try {
                                    stream.setStrokingColor(r, g, b)
                                    stream.setLineWidth(0.75f)
                                    stream.moveTo(chunkX, lineBaseY - 1.5f)
                                    stream.lineTo(chunkX + c.widthPt, lineBaseY - 1.5f)
                                    stream.stroke()
                                } catch (_: Throwable) {}
                            }

                            if (c.isStrikethrough) {
                                try {
                                    stream.setStrokingColor(r, g, b)
                                    stream.setLineWidth(0.75f)
                                    stream.moveTo(chunkX, lineBaseY + c.fontSize * 0.3f)
                                    stream.lineTo(chunkX + c.widthPt, lineBaseY + c.fontSize * 0.3f)
                                    stream.stroke()
                                } catch (_: Throwable) {}
                            }

                            chunkX += c.widthPt
                        }
                        is SpaceChunk -> {
                            chunkX += c.widthPt + extraPerSpace
                        }
                        is ImageChunk -> {
                            if (c.bitmap != null) {
                                try {
                                    val pdImg = LosslessFactory.createFromImage(pdfDocument, c.bitmap)
                                    stream.drawImage(pdImg, chunkX, currentY - c.heightPt, c.widthPt, c.heightPt)
                                } catch (_: Throwable) {}
                            }
                            chunkX += c.widthPt
                        }
                        else -> {}
                    }
                }

                currentY -= line.heightPt
            }

            currentY -= p.spaceAfterPt
        }

        for (element in docx.elements) {
            when (element) {
                is ParagraphElement -> {
                    renderParagraph(element)
                }
                is TableElement -> {
                    val numCols = element.rows.maxOfOrNull {
                        it.cells.sumOf { c -> c.colSpan }
                    } ?: 1

                    val colWidths = mutableListOf<Float>()
                    if (element.columnWidthsPt.size >= numCols) {
                        val gridSum = element.columnWidthsPt.take(numCols).sum()
                        if (gridSum > 0f) {
                            val scale = printWidth / gridSum
                            for (w in element.columnWidthsPt.take(numCols)) {
                                colWidths.add(w * scale)
                            }
                        }
                    }

                    if (colWidths.size < numCols) {
                        colWidths.clear()
                        val defaultColWidth = printWidth / numCols.toFloat()
                        repeat(numCols) {
                            colWidths.add(defaultColWidth)
                        }
                    }

                    for (row in element.rows) {
                        val padding = 4f
                        val cellHeights = mutableListOf<Float>()

                        var cIdx = 0
                        for (cell in row.cells) {
                            var cellWidth = 0f
                            for (span in 0 until cell.colSpan) {
                                if (cIdx + span < colWidths.size) {
                                    cellWidth += colWidths[cIdx + span]
                                }
                            }
                            cIdx += cell.colSpan

                            var cellContentHeight = 0f
                            val cellAvailWidth = maxOf(10f, cellWidth - padding * 2f)

                            for (el in cell.elements) {
                                if (el is ParagraphElement) {
                                    val pChunks = createInlineChunks(el.inlines, pdfDocument)
                                    val pLines = layoutParagraphLines(
                                        chunks = pChunks,
                                        availableWidthFirstLine = cellAvailWidth,
                                        availableWidthOtherLines = cellAvailWidth,
                                        lineSpacingMult = el.lineSpacingMult
                                    )
                                    val pHeight = pLines.sumOf { it.heightPt.toDouble() }.toFloat() + el.spaceBeforePt + el.spaceAfterPt
                                    cellContentHeight += pHeight
                                }
                            }

                            cellHeights.add(maxOf(18f, cellContentHeight + padding * 2f))
                        }

                        val rowHeight = maxOf(20f, cellHeights.maxOrNull() ?: 20f)
                        ensureSpace(rowHeight + 2f)

                        val cellTopY = currentY

                        var colLeftX = docx.marginLeftPt
                        cIdx = 0
                        for (cell in row.cells) {
                            var cellWidth = 0f
                            for (span in 0 until cell.colSpan) {
                                if (cIdx + span < colWidths.size) {
                                    cellWidth += colWidths[cIdx + span]
                                }
                            }
                            cIdx += cell.colSpan

                            try {
                                if (cell.backgroundColor != null) {
                                    val r = (cell.backgroundColor shr 16) and 0xFF
                                    val g = (cell.backgroundColor shr 8) and 0xFF
                                    val b = cell.backgroundColor and 0xFF
                                    stream.setNonStrokingColor(r, g, b)
                                    stream.addRect(colLeftX, cellTopY - rowHeight, cellWidth, rowHeight)
                                    stream.fill()
                                }

                                stream.setStrokingColor(200, 200, 200)
                                stream.setLineWidth(0.5f)
                                stream.addRect(colLeftX, cellTopY - rowHeight, cellWidth, rowHeight)
                                stream.stroke()
                            } catch (_: Throwable) {}

                            val originalY = currentY
                            currentY = cellTopY - padding

                            for (el in cell.elements) {
                                if (el is ParagraphElement) {
                                    renderParagraph(
                                        p = el,
                                        containerAvailableWidth = cellWidth - padding * 2f,
                                        containerLeftMargin = colLeftX + padding
                                    )
                                }
                            }

                            currentY = originalY
                            colLeftX += cellWidth
                        }

                        currentY -= rowHeight
                    }
                    currentY -= 8f
                }
                is ImageBlockElement -> {
                    try {
                        val bitmap = BitmapFactory.decodeByteArray(element.imageBytes, 0, element.imageBytes.size)
                        if (bitmap != null) {
                            var imgW = element.widthPt
                            var imgH = element.heightPt

                            if (imgW > printWidth) {
                                val ratio = printWidth / imgW
                                imgW = printWidth
                                imgH *= ratio
                            }

                            ensureSpace(imgH + 8f)

                            val pdImage = LosslessFactory.createFromImage(pdfDocument, bitmap)
                            val imgX = when (element.alignment) {
                                Alignment.CENTER -> docx.marginLeftPt + (printWidth - imgW) / 2f
                                Alignment.RIGHT -> docx.marginLeftPt + (printWidth - imgW)
                                else -> docx.marginLeftPt
                            }

                            stream.drawImage(pdImage, imgX, currentY - imgH, imgW, imgH)
                            currentY -= (imgH + 8f)
                            bitmap.recycle()
                        }
                    } catch (_: Exception) {}
                }
                is PageBreakElement -> {
                    ensureSpace(docx.pageHeightPt)
                }
            }
        }

        try { stream.close() } catch (_: Throwable) {}
        return pageNumber
    }

    private fun sanitizeText(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                '’', '‘' -> sb.append('\'')
                '“', '”' -> sb.append('"')
                '–', '—' -> sb.append('-')
                '…' -> sb.append("...")
                '\t' -> sb.append("    ")
                '\r', '\n' -> sb.append(' ')
                in ' '..'~' -> sb.append(ch)
                else -> {
                    if (ch.code in 128..255) {
                        sb.append(ch)
                    } else {
                        sb.append(' ')
                    }
                }
            }
        }
        return sb.toString()
    }
}
