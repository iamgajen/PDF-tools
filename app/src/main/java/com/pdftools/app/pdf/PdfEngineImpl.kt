package com.pdftools.app.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.pdftools.app.domain.pdf.CompressionLevel
import com.pdftools.app.domain.pdf.ImageSpec
import com.pdftools.app.domain.pdf.ImageStreamSpec
import com.pdftools.app.domain.pdf.ImageToPdfPageSize
import com.pdftools.app.domain.pdf.ImageToPdfQuality
import com.pdftools.app.domain.pdf.OrganizePageSpec
import com.pdftools.app.domain.pdf.PdfToImageFormat
import com.pdftools.app.domain.pdf.PdfToImageQuality
import com.pdftools.app.domain.pdf.PdfToImageResolution
import com.pdftools.app.domain.pdf.SignatureSpec
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class PdfEngineImpl(
    private val pdfValidator: PdfValidator = PdfValidator()
) : PdfEngine {

    override fun validatePdf(contentResolver: ContentResolver, uri: Uri): PdfValidationResult {
        return pdfValidator.validate(contentResolver, uri)
    }

    override fun validatePdfStream(inputStreamProvider: () -> InputStream?): PdfValidationResult {
        return pdfValidator.validate(inputStreamProvider)
    }

    override fun mergePdfs(
        contentResolver: ContentResolver,
        inputUris: List<Uri>,
        outputStream: OutputStream
    ): Result<Int> {
        val providers = inputUris.map { uri ->
            { contentResolver.openInputStream(uri) }
        }
        return mergePdfStreams(providers, outputStream)
    }

    override fun mergePdfStreams(
        streamProviders: List<() -> InputStream?>,
        outputStream: OutputStream
    ): Result<Int> {
        if (streamProviders.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please select at least two PDF files to merge."))
        }

        var destinationDoc: PDDocument? = null
        val sourceDocs = mutableListOf<PDDocument>()
        val inputStreams = mutableListOf<InputStream>()

        try {
            destinationDoc = PDDocument()
            val merger = PDFMergerUtility()

            for ((index, provider) in streamProviders.withIndex()) {
                val inputStream = provider()
                    ?: return Result.failure(IOException("Unable to access file at position ${index + 1}."))
                inputStreams.add(inputStream)

                val sourceDoc = try {
                    PDDocument.load(inputStream)
                } catch (e: InvalidPasswordException) {
                    return Result.failure(IllegalStateException("This PDF is password protected.", e))
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true ||
                        e.cause?.message?.contains("password", ignoreCase = true) == true
                    ) {
                        return Result.failure(IllegalStateException("This PDF is password protected.", e))
                    }
                    return Result.failure(IOException("Unable to read PDF file at position ${index + 1}.", e))
                }

                sourceDocs.add(sourceDoc)

                if (sourceDoc.isEncrypted) {
                    return Result.failure(IllegalStateException("This PDF is password protected."))
                }

                merger.appendDocument(destinationDoc, sourceDoc)
            }

            destinationDoc.save(outputStream)
            val pageCount = destinationDoc.numberOfPages
            return Result.success(pageCount)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("password", ignoreCase = true)) {
                return Result.failure(IllegalStateException("This PDF is password protected.", e))
            }
            return Result.failure(IOException(if (msg.isNotBlank()) msg else "Failed to merge PDF files.", e))
        } finally {
            for (doc in sourceDocs) {
                try {
                    doc.close()
                } catch (_: Exception) {
                }
            }
            for (stream in inputStreams) {
                try {
                    stream.close()
                } catch (_: Exception) {
                }
            }
            try {
                destinationDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    override fun splitPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        pageGroups: List<List<Int>>,
        outputStreamProviders: List<() -> OutputStream>
    ): Result<List<Int>> {
        return splitPdfStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            pageGroups = pageGroups,
            outputStreamProviders = outputStreamProviders
        )
    }

    override fun splitPdfStreams(
        inputStreamProvider: () -> InputStream?,
        pageGroups: List<List<Int>>,
        outputStreamProviders: List<() -> OutputStream>
    ): Result<List<Int>> {
        if (pageGroups.isEmpty()) {
            return Result.failure(IllegalArgumentException("No page ranges provided for splitting."))
        }
        if (pageGroups.size != outputStreamProviders.size) {
            return Result.failure(IllegalArgumentException("Output stream count does not match page groups count."))
        }

        val inputStream = inputStreamProvider()
            ?: return Result.failure(IOException("Unable to access source PDF file."))

        var sourceDoc: PDDocument? = null
        val pageCounts = mutableListOf<Int>()

        try {
            inputStream.use { stream ->
                try {
                    sourceDoc = PDDocument.load(stream)
                } catch (e: InvalidPasswordException) {
                    return Result.failure(IllegalStateException("This PDF is password protected.", e))
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        return Result.failure(IllegalStateException("This PDF is password protected.", e))
                    }
                    return Result.failure(IOException("Unable to read source PDF file.", e))
                }

                val doc = sourceDoc ?: return Result.failure(IOException("Failed to load PDF file."))
                if (doc.isEncrypted) {
                    return Result.failure(IllegalStateException("This PDF is password protected."))
                }

                val totalPages = doc.numberOfPages

                for ((index, group) in pageGroups.withIndex()) {
                    if (group.isEmpty()) {
                        return Result.failure(IllegalArgumentException("Page group at position ${index + 1} is empty."))
                    }

                    for (pageIdx in group) {
                        if (pageIdx < 0 || pageIdx >= totalPages) {
                            return Result.failure(IllegalArgumentException("Page index ${pageIdx + 1} is out of bounds (1..$totalPages)."))
                        }
                    }

                    val outputStream = outputStreamProviders[index]()
                    var splitDoc: PDDocument? = null
                    try {
                        splitDoc = PDDocument()
                        for (pageIdx in group) {
                            splitDoc.addPage(doc.getPage(pageIdx))
                        }
                        outputStream.use { outStream ->
                            splitDoc.save(outStream)
                        }
                        pageCounts.add(splitDoc.numberOfPages)
                    } finally {
                        try {
                            splitDoc?.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            return Result.success(pageCounts)
        } catch (e: Exception) {
            return Result.failure(IOException(e.localizedMessage ?: "Failed to split PDF file.", e))
        } finally {
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    override fun organizePdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        pageSpecs: List<OrganizePageSpec>,
        outputStream: OutputStream
    ): Result<Int> {
        return organizePdfStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            pageSpecs = pageSpecs,
            outputStream = outputStream
        )
    }

    override fun organizePdfStreams(
        inputStreamProvider: () -> InputStream?,
        pageSpecs: List<OrganizePageSpec>,
        outputStream: OutputStream
    ): Result<Int> {
        if (pageSpecs.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one page must be included in the organized PDF."))
        }

        val inputStream = inputStreamProvider()
            ?: return Result.failure(IOException("Unable to access source PDF file."))

        var sourceDoc: PDDocument? = null
        var organizedDoc: PDDocument? = null

        try {
            inputStream.use { stream ->
                try {
                    sourceDoc = PDDocument.load(stream)
                } catch (e: InvalidPasswordException) {
                    return Result.failure(IllegalStateException("This PDF is password protected.", e))
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        return Result.failure(IllegalStateException("This PDF is password protected.", e))
                    }
                    return Result.failure(IOException("Unable to read source PDF file.", e))
                }

                val doc = sourceDoc ?: return Result.failure(IOException("Failed to load PDF file."))
                if (doc.isEncrypted) {
                    return Result.failure(IllegalStateException("This PDF is password protected."))
                }

                val totalPages = doc.numberOfPages

                for (spec in pageSpecs) {
                    if (spec.originalPageIndex < 0 || spec.originalPageIndex >= totalPages) {
                        return Result.failure(IllegalArgumentException("Page index ${spec.originalPageIndex + 1} is out of bounds (1..$totalPages)."))
                    }
                }

                organizedDoc = PDDocument()
                for (spec in pageSpecs) {
                    val originalPage = doc.getPage(spec.originalPageIndex)
                    val existingRotation = originalPage.rotation
                    val newRotation = (existingRotation + spec.rotationDegrees) % 360
                    val normalizedRotation = if (newRotation < 0) newRotation + 360 else newRotation
                    originalPage.rotation = normalizedRotation
                    organizedDoc?.addPage(originalPage)
                }

                outputStream.use { out ->
                    organizedDoc?.save(out)
                }

                val pageCount = organizedDoc?.numberOfPages ?: pageSpecs.size
                return Result.success(pageCount)
            }
        } catch (e: Exception) {
            return Result.failure(IOException(e.localizedMessage ?: "Failed to organize PDF file.", e))
        } finally {
            try {
                organizedDoc?.close()
            } catch (_: Exception) {
            }
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    override fun compressPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        level: CompressionLevel,
        outputStream: OutputStream
    ): Result<Int> {
        return compressPdfStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            level = level,
            outputStream = outputStream
        )
    }

    override fun compressPdfStreams(
        inputStreamProvider: () -> InputStream?,
        level: CompressionLevel,
        outputStream: OutputStream
    ): Result<Int> {
        val inputStream = inputStreamProvider()
            ?: return Result.failure(IOException("Unable to access source PDF file."))

        var sourceDoc: PDDocument? = null

        try {
            inputStream.use { stream ->
                try {
                    sourceDoc = PDDocument.load(stream)
                } catch (e: InvalidPasswordException) {
                    return Result.failure(IllegalStateException("This PDF is password protected.", e))
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        return Result.failure(IllegalStateException("This PDF is password protected.", e))
                    }
                    return Result.failure(IOException("Unable to read source PDF file.", e))
                }

                val doc = sourceDoc ?: return Result.failure(IOException("Failed to load PDF file."))
                if (doc.isEncrypted) {
                    return Result.failure(IllegalStateException("This PDF is password protected."))
                }

                for (page in doc.pages) {
                    val resources = page.resources ?: continue
                    for (name in resources.xObjectNames) {
                        try {
                            if (resources.isImageXObject(name)) {
                                val imageXObject = resources.getXObject(name) as? PDImageXObject
                                    ?: continue
                                optimizeImageXObject(doc, resources, name, imageXObject, level)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                outputStream.use { out ->
                    doc.save(out)
                }

                return Result.success(doc.numberOfPages)
            }
        } catch (e: Exception) {
            return Result.failure(IOException(e.localizedMessage ?: "Failed to compress PDF file.", e))
        } finally {
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun optimizeImageXObject(
        doc: PDDocument,
        resources: PDResources,
        name: COSName,
        imageXObject: PDImageXObject,
        level: CompressionLevel
    ) {
        val origWidth = imageXObject.width
        val origHeight = imageXObject.height

        if (origWidth < 120 && origHeight < 120) return

        val originalBitmap = try {
            imageXObject.image
        } catch (_: Exception) {
            null
        } ?: return

        try {
            val maxDim = level.targetMaxDimension
            val scaledBitmap = if (origWidth > maxDim || origHeight > maxDim) {
                val scaleRatio = minOf(maxDim.toFloat() / origWidth, maxDim.toFloat() / origHeight)
                val targetW = (origWidth * scaleRatio).toInt().coerceAtLeast(1)
                val targetH = (origHeight * scaleRatio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val compressedImageXObject = JPEGFactory.createFromImage(
                doc,
                scaledBitmap,
                level.jpegQuality
            )

            val origLen = imageXObject.cosObject.length
            val newLen = compressedImageXObject.cosObject.length

            if (newLen > 0 && (origLen <= 0 || newLen < origLen || scaledBitmap != originalBitmap)) {
                resources.put(name, compressedImageXObject)
            }

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
        } catch (_: Exception) {
        } finally {
            originalBitmap.recycle()
        }
    }

    override fun convertImagesToPdf(
        contentResolver: ContentResolver,
        images: List<ImageSpec>,
        pageSize: ImageToPdfPageSize,
        quality: ImageToPdfQuality,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        val streamSpecs = images.map { spec ->
            ImageStreamSpec(
                inputStreamProvider = { contentResolver.openInputStream(spec.uri) },
                rotationDegrees = spec.rotationDegrees,
                name = spec.name
            )
        }
        return convertImagesToPdfStreams(streamSpecs, pageSize, quality, outputStream, onProgress)
    }

    override fun convertImagesToPdfStreams(
        imageSpecs: List<ImageStreamSpec>,
        pageSize: ImageToPdfPageSize,
        quality: ImageToPdfQuality,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        if (imageSpecs.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please select at least one image to create a PDF."))
        }

        var doc: PDDocument? = null
        try {
            doc = PDDocument()
            val total = imageSpecs.size

            for ((index, spec) in imageSpecs.withIndex()) {
                onProgress?.invoke(index + 1, total)

                // 1. Read EXIF Orientation
                val exifRotation = try {
                    spec.inputStreamProvider()?.use { readExifOrientation(it) } ?: 0
                } catch (_: Exception) {
                    0
                }

                // 2. Decode downsampled bitmap based on quality
                val rawBitmap = decodeSampledBitmap(spec.inputStreamProvider, quality.targetMaxDimension)
                    ?: return Result.failure(IOException("Unable to decode image '${spec.name}'. The file may be corrupted or unsupported."))

                // 3. Apply total rotation (EXIF + user rotation)
                val totalRotation = (exifRotation + spec.rotationDegrees) % 360
                val normalizedRotation = if (totalRotation < 0) totalRotation + 360 else totalRotation

                val rotatedBitmap = if (normalizedRotation != 0) {
                    try {
                        val matrix = Matrix().apply { postRotate(normalizedRotation.toFloat()) }
                        val rotated = Bitmap.createBitmap(
                            rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                        )
                        if (rotated != rawBitmap) {
                            rawBitmap.recycle()
                        }
                        rotated
                    } catch (e: Exception) {
                        rawBitmap.recycle()
                        return Result.failure(IOException("Failed to rotate image '${spec.name}'.", e))
                    }
                } else {
                    rawBitmap
                }

                val bmpWidth = rotatedBitmap.width.toFloat()
                val bmpHeight = rotatedBitmap.height.toFloat()

                // 4. Page dimensions
                val (pageWidth, pageHeight) = when (pageSize) {
                    ImageToPdfPageSize.ORIGINAL -> {
                        Pair(bmpWidth, bmpHeight)
                    }
                    ImageToPdfPageSize.A4 -> {
                        if (bmpWidth > bmpHeight) {
                            Pair(ImageToPdfPageSize.A4.heightPoints, ImageToPdfPageSize.A4.widthPoints)
                        } else {
                            Pair(ImageToPdfPageSize.A4.widthPoints, ImageToPdfPageSize.A4.heightPoints)
                        }
                    }
                    ImageToPdfPageSize.LETTER -> {
                        if (bmpWidth > bmpHeight) {
                            Pair(ImageToPdfPageSize.LETTER.heightPoints, ImageToPdfPageSize.LETTER.widthPoints)
                        } else {
                            Pair(ImageToPdfPageSize.LETTER.widthPoints, ImageToPdfPageSize.LETTER.heightPoints)
                        }
                    }
                }

                val margin = if (pageSize == ImageToPdfPageSize.ORIGINAL) 0f else 20f
                val availWidth = (pageWidth - 2 * margin).coerceAtLeast(1f)
                val availHeight = (pageHeight - 2 * margin).coerceAtLeast(1f)

                val scaleRatio = if (pageSize == ImageToPdfPageSize.ORIGINAL) 1f else {
                    minOf(availWidth / bmpWidth, availHeight / bmpHeight)
                }

                val drawWidth = bmpWidth * scaleRatio
                val drawHeight = bmpHeight * scaleRatio
                val drawX = (pageWidth - drawWidth) / 2f
                val drawY = (pageHeight - drawHeight) / 2f

                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                doc.addPage(page)

                try {
                    val pdImage = JPEGFactory.createFromImage(doc, rotatedBitmap, quality.jpegQuality)
                    PDPageContentStream(doc, page).use { contentStream ->
                        contentStream.drawImage(pdImage, drawX, drawY, drawWidth, drawHeight)
                    }
                } finally {
                    rotatedBitmap.recycle()
                }
            }

            outputStream.use { out ->
                doc.save(out)
            }

            return Result.success(doc.numberOfPages)
        } catch (e: Exception) {
            return Result.failure(IOException(e.localizedMessage ?: "Failed to create PDF from images.", e))
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun readExifOrientation(inputStream: InputStream): Int {
        return try {
            val exif = ExifInterface(inputStream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun decodeSampledBitmap(
        inputStreamProvider: () -> InputStream?,
        targetMaxDimension: Int
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        try {
            inputStreamProvider()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return null
        } catch (_: Exception) {
            return null
        }

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        var inSampleSize = 1
        val maxDim = maxOf(origWidth, origHeight)
        if (maxDim > targetMaxDimension) {
            val halfMax = maxDim / 2
            while ((halfMax / inSampleSize) >= targetMaxDimension) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return try {
            inputStreamProvider()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun convertPdfToImages(
        contentResolver: ContentResolver,
        inputUri: Uri,
        format: PdfToImageFormat,
        quality: PdfToImageQuality,
        resolution: PdfToImageResolution,
        pagesToRender: List<Int>,
        outputStreamProviders: List<() -> OutputStream>,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        return convertPdfToImageStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            fileDescriptorProvider = {
                try {
                    contentResolver.openFileDescriptor(inputUri, "r")
                } catch (_: Exception) {
                    null
                }
            },
            format = format,
            quality = quality,
            resolution = resolution,
            pagesToRender = pagesToRender,
            outputStreamProviders = outputStreamProviders,
            onProgress = onProgress
        )
    }

    override fun convertPdfToImageStreams(
        inputStreamProvider: () -> InputStream?,
        fileDescriptorProvider: (() -> ParcelFileDescriptor?)?,
        format: PdfToImageFormat,
        quality: PdfToImageQuality,
        resolution: PdfToImageResolution,
        pagesToRender: List<Int>,
        outputStreamProviders: List<() -> OutputStream>,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        if (pagesToRender.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please select at least one page to convert."))
        }
        if (pagesToRender.size != outputStreamProviders.size) {
            return Result.failure(IllegalArgumentException("Output stream count does not match pages count."))
        }

        var sourceDoc: PDDocument? = null
        var pfdToClose: ParcelFileDescriptor? = null
        var tempFile: File? = null

        try {
            val inputStream = inputStreamProvider()
                ?: return Result.failure(IOException("Unable to access source PDF file."))

            val totalPages = inputStream.use { stream ->
                try {
                    sourceDoc = PDDocument.load(stream)
                } catch (e: InvalidPasswordException) {
                    return Result.failure(IllegalStateException("This PDF is password protected.", e))
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        return Result.failure(IllegalStateException("This PDF is password protected.", e))
                    }
                    return Result.failure(IOException("Unable to read source PDF file.", e))
                }

                val doc = sourceDoc ?: return Result.failure(IOException("Failed to load PDF file."))
                if (doc.isEncrypted) {
                    return Result.failure(IllegalStateException("This PDF is password protected."))
                }
                doc.numberOfPages
            }

            for (pageIdx in pagesToRender) {
                if (pageIdx < 0 || pageIdx >= totalPages) {
                    return Result.failure(IllegalArgumentException("Page index ${pageIdx + 1} is out of bounds (1..$totalPages)."))
                }
            }

            val pfd: ParcelFileDescriptor = fileDescriptorProvider?.invoke() ?: run {
                val tempStream = inputStreamProvider()
                    ?: return Result.failure(IOException("Unable to access source PDF file."))
                tempFile = File.createTempFile("temp_pdf_render_", ".pdf")
                FileOutputStream(tempFile!!).use { out ->
                    tempStream.use { inp -> inp.copyTo(out) }
                }
                val createdPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                pfdToClose = createdPfd
                createdPfd
            }

            if (pfdToClose == null && fileDescriptorProvider == null) {
                pfdToClose = pfd
            }

            var renderer: PdfRenderer? = null
            try {
                renderer = PdfRenderer(pfd)
                val totalSteps = pagesToRender.size

                for ((stepIdx, pageIdx) in pagesToRender.withIndex()) {
                    onProgress?.invoke(stepIdx + 1, totalSteps)

                    val page = renderer.openPage(pageIdx)
                    var targetWidth = (page.width * resolution.scaleFactor).toInt().coerceAtLeast(1)
                    var targetHeight = (page.height * resolution.scaleFactor).toInt().coerceAtLeast(1)

                    val maxDim = 4000
                    if (targetWidth > maxDim || targetHeight > maxDim) {
                        val ratio = minOf(maxDim.toFloat() / targetWidth, maxDim.toFloat() / targetHeight)
                        targetWidth = (targetWidth * ratio).toInt().coerceAtLeast(1)
                        targetHeight = (targetHeight * ratio).toInt().coerceAtLeast(1)
                    }

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val outputStream = outputStreamProviders[stepIdx]()
                    outputStream.use { out ->
                        if (format == PdfToImageFormat.JPG) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.compressQuality, out)
                        } else {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }

                    bitmap.recycle()
                }

                return Result.success(pagesToRender.size)
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("password", ignoreCase = true)) {
                return Result.failure(IllegalStateException("This PDF is password protected.", e))
            }
            return Result.failure(IOException(if (msg.isNotBlank()) msg else "Failed to convert PDF to images.", e))
        } finally {
            try { pfdToClose?.close() } catch (_: Exception) {}
            try { tempFile?.delete() } catch (_: Exception) {}
            try { sourceDoc?.close() } catch (_: Exception) {}
        }
    }

    override fun protectPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        password: String,
        preventPrinting: Boolean,
        preventCopying: Boolean,
        preventModifying: Boolean,
        outputStream: OutputStream
    ): Result<Int> {
        return protectPdfStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            password = password,
            preventPrinting = preventPrinting,
            preventCopying = preventCopying,
            preventModifying = preventModifying,
            outputStream = outputStream
        )
    }

    override fun protectPdfStreams(
        inputStreamProvider: () -> InputStream?,
        password: String,
        preventPrinting: Boolean,
        preventCopying: Boolean,
        preventModifying: Boolean,
        outputStream: OutputStream
    ): Result<Int> {
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty."))
        }

        val inputStream = inputStreamProvider()
            ?: return Result.failure(IOException("Unable to access source PDF file."))

        var sourceDoc: PDDocument? = null
        try {
            inputStream.use { stream ->
                try {
                    sourceDoc = PDDocument.load(stream)
                } catch (e: InvalidPasswordException) {
                    return Result.failure(IllegalStateException("This PDF is already password protected.", e))
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        return Result.failure(IllegalStateException("This PDF is already password protected.", e))
                    }
                    return Result.failure(IOException("Unable to read source PDF file.", e))
                }

                val doc = sourceDoc ?: return Result.failure(IOException("Failed to load PDF file."))
                if (doc.isEncrypted) {
                    return Result.failure(IllegalStateException("This PDF is already password protected."))
                }

                val ap = AccessPermission()
                if (preventPrinting) {
                    ap.setCanPrint(false)
                }
                if (preventCopying) {
                    ap.setCanExtractContent(false)
                }
                if (preventModifying) {
                    ap.setCanModify(false)
                }

                val spp = StandardProtectionPolicy(password, password, ap)
                spp.encryptionKeyLength = 128
                spp.permissions = ap

                doc.protect(spp)

                outputStream.use { out ->
                    doc.save(out)
                }

                return Result.success(doc.numberOfPages)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("password", ignoreCase = true)) {
                return Result.failure(IllegalStateException("This PDF is already password protected.", e))
            }
            return Result.failure(IOException(if (msg.isNotBlank()) msg else "Failed to protect PDF file.", e))
        } finally {
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    override fun unlockPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        password: String,
        outputStream: OutputStream
    ): Result<Int> {
        return unlockPdfStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            password = password,
            outputStream = outputStream
        )
    }

    override fun unlockPdfStreams(
        inputStreamProvider: () -> InputStream?,
        password: String,
        outputStream: OutputStream
    ): Result<Int> {
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty."))
        }

        val inputStream = inputStreamProvider()
            ?: return Result.failure(IOException("Unable to access source PDF file."))

        var sourceDoc: PDDocument? = null
        try {
            inputStream.use { stream ->
                try {
                    sourceDoc = PDDocument.load(stream, password)
                } catch (e: InvalidPasswordException) {
                    return Result.failure(IllegalArgumentException("Incorrect password.", e))
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("password", ignoreCase = true) || e.cause?.message?.contains("password", ignoreCase = true) == true) {
                        return Result.failure(IllegalArgumentException("Incorrect password.", e))
                    }
                    return Result.failure(IOException("Unable to read source PDF file.", e))
                }

                val doc = sourceDoc ?: return Result.failure(IOException("Failed to load PDF file."))
                doc.isAllSecurityToBeRemoved = true

                outputStream.use { out ->
                    doc.save(out)
                }

                return Result.success(doc.numberOfPages)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("password", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Incorrect password.", e))
            }
            return Result.failure(IOException(if (msg.isNotBlank()) msg else "Failed to unlock PDF file.", e))
        } finally {
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    override fun signPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        signatures: List<SignatureSpec>,
        outputStream: OutputStream
    ): Result<Int> {
        return signPdfStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            signatures = signatures,
            outputStream = outputStream
        )
    }

    override fun signPdfStreams(
        inputStreamProvider: () -> InputStream?,
        signatures: List<SignatureSpec>,
        outputStream: OutputStream
    ): Result<Int> {
        if (signatures.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please add at least one signature to sign the PDF."))
        }

        val inputStream = inputStreamProvider()
            ?: return Result.failure(IOException("Unable to access source PDF file."))

        var sourceDoc: PDDocument? = null
        try {
            inputStream.use { stream ->
                try {
                    sourceDoc = PDDocument.load(stream)
                } catch (e: InvalidPasswordException) {
                    return Result.failure(IllegalStateException("This PDF is password protected.", e))
                } catch (e: Exception) {
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        return Result.failure(IllegalStateException("This PDF is password protected.", e))
                    }
                    return Result.failure(IOException("Unable to read source PDF file.", e))
                }

                val doc = sourceDoc ?: return Result.failure(IOException("Failed to load PDF file."))
                if (doc.isEncrypted) {
                    return Result.failure(IllegalStateException("This PDF is password protected."))
                }

                val totalPages = doc.numberOfPages
                val sigsByPage = signatures.groupBy { it.pageIndex }

                for ((pageIdx, sigs) in sigsByPage) {
                    if (pageIdx < 0 || pageIdx >= totalPages) {
                        continue
                    }
                    val page = doc.getPage(pageIdx)
                    val box = page.cropBox ?: page.mediaBox
                    val L = box.lowerLeftX
                    val B = box.lowerLeftY
                    val W0 = box.width
                    val H0 = box.height
                    val rotationDeg = (page.rotation % 360 + 360) % 360

                    val VW = if (rotationDeg == 90 || rotationDeg == 270) H0 else W0
                    val VH = if (rotationDeg == 90 || rotationDeg == 270) W0 else H0

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                        for (sig in sigs) {
                            val vx = sig.normalizedX * VW
                            val vy = sig.normalizedY * VH
                            val vw = sig.normalizedWidth * VW
                            val vh = sig.normalizedHeight * VH

                            val vcx = vx + vw / 2f
                            val vcy = vy + vh / 2f

                            val cxPdf = when (rotationDeg) {
                                90 -> L + vcy
                                180 -> L + W0 - vcx
                                270 -> L + W0 - vcy
                                else -> L + vcx
                            }
                            val cyPdf = when (rotationDeg) {
                                90 -> B + H0 - vcx
                                180 -> B + vcy
                                270 -> B + vcx
                                else -> B + H0 - vcy
                            }

                            val totalRot = (sig.rotationDegrees - rotationDeg).toInt()
                            val normalizedRot = (totalRot % 360 + 360) % 360

                            val bmp = sig.bitmap
                            if (bmp != null) {
                                val rotatedBmp = if (normalizedRot != 0) {
                                    val matrix = Matrix().apply { postRotate(normalizedRot.toFloat()) }
                                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                } else {
                                    bmp
                                }

                                val wPdf = if (normalizedRot == 90 || normalizedRot == 270) vh else vw
                                val hPdf = if (normalizedRot == 90 || normalizedRot == 270) vw else vh

                                val xPdf = cxPdf - wPdf / 2f
                                val yPdf = cyPdf - hPdf / 2f

                                val pdImage = try {
                                    LosslessFactory.createFromImage(doc, rotatedBmp)
                                } catch (_: Throwable) {
                                    null
                                }

                                if (pdImage != null) {
                                    contentStream.drawImage(pdImage, xPdf, yPdf, wPdf, hPdf)
                                }

                                if (rotatedBmp != bmp) {
                                    rotatedBmp.recycle()
                                }
                            }
                        }
                    }
                }

                outputStream.use { out ->
                    doc.save(out)
                }

                return Result.success(doc.numberOfPages)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("password", ignoreCase = true)) {
                return Result.failure(IllegalStateException("This PDF is password protected.", e))
            }
            return Result.failure(IOException(if (msg.isNotBlank()) msg else "Failed to sign PDF file.", e))
        } finally {
            try {
                sourceDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    override fun convertWordToPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        return convertWordToPdfStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            outputStream = outputStream,
            onProgress = onProgress
        )
    }

    override fun convertWordToPdfStreams(
        inputStreamProvider: () -> InputStream?,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        val inputStream = inputStreamProvider()
            ?: return Result.failure(IOException("Unable to access Word document."))

        return inputStream.use { stream ->
            val converter = WordToPdfConverter()
            converter.convert(stream, outputStream, onProgress)
        }
    }

    override fun convertPdfToWord(
        contentResolver: ContentResolver,
        inputUri: Uri,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        return convertPdfToWordStreams(
            inputStreamProvider = { contentResolver.openInputStream(inputUri) },
            outputStream = outputStream,
            onProgress = onProgress
        )
    }

    override fun convertPdfToWordStreams(
        inputStreamProvider: () -> InputStream?,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<Int> {
        val inputStream = inputStreamProvider()
            ?: return Result.failure(IOException("Unable to access PDF document."))

        return inputStream.use { stream ->
            val converter = PdfToWordConverter()
            converter.convert(stream, outputStream, onProgress)
        }
    }
}
