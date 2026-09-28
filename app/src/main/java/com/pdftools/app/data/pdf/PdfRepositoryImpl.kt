package com.pdftools.app.data.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.pdftools.app.domain.pdf.CompressPdfResult
import com.pdftools.app.domain.pdf.CompressionLevel
import com.pdftools.app.domain.pdf.ImageSpec
import com.pdftools.app.domain.pdf.ImageToPdfPageSize
import com.pdftools.app.domain.pdf.ImageToPdfQuality
import com.pdftools.app.domain.pdf.ImageToPdfResult
import com.pdftools.app.domain.pdf.OrganizeOutputFile
import com.pdftools.app.domain.pdf.OrganizePageSpec
import com.pdftools.app.domain.pdf.PdfFile
import com.pdftools.app.domain.pdf.SplitMode
import com.pdftools.app.domain.pdf.SplitOutputFile
import com.pdftools.app.domain.pdf.PdfToImageOutputFile
import com.pdftools.app.domain.pdf.PdfToImageParams
import com.pdftools.app.domain.pdf.PdfToImageResult
import com.pdftools.app.domain.pdf.ProtectPdfParams
import com.pdftools.app.domain.pdf.ProtectUnlockOutputFile
import com.pdftools.app.domain.pdf.SignOutputFile
import com.pdftools.app.domain.pdf.SignPdfParams
import com.pdftools.app.domain.pdf.UnlockPdfParams
import com.pdftools.app.domain.pdf.WordToPdfResult
import com.pdftools.app.domain.pdf.PdfToWordResult
import com.pdftools.app.pdf.PdfEngine
import com.pdftools.app.pdf.PdfEngineImpl
import com.pdftools.app.pdf.PdfValidationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

class PdfRepositoryImpl(
    private val context: Context,
    private val pdfEngine: PdfEngine = PdfEngineImpl(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PdfRepository {

    override suspend fun getPdfFileInfo(uri: Uri): PdfFile = withContext(ioDispatcher) {
        val baseInfo = readMetaData(context, uri)
        val validation = pdfEngine.validatePdf(context.contentResolver, uri)
        when (validation) {
            is PdfValidationResult.Valid -> baseInfo.copy(
                pageCount = validation.pageCount,
                isValid = true
            )
            is PdfValidationResult.Encrypted -> baseInfo.copy(
                isEncrypted = true,
                isValid = false,
                errorMessage = validation.message
            )
            is PdfValidationResult.Invalid -> baseInfo.copy(
                isValid = false,
                errorMessage = validation.message
            )
            is PdfValidationResult.Unreadable -> baseInfo.copy(
                isValid = false,
                errorMessage = validation.message
            )
        }
    }

    override suspend fun mergePdfs(
        inputUris: List<Uri>,
        outputStream: OutputStream
    ): Result<Int> = withContext(ioDispatcher) {
        pdfEngine.mergePdfs(context.contentResolver, inputUris, outputStream)
    }

    override suspend fun createTempMergedFile(inputUris: List<Uri>): Result<File> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "merged_pdfs")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val outputFile = File(outputDir, "merged_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outputFile).use { outStream ->
                val result = pdfEngine.mergePdfs(context.contentResolver, inputUris, outStream)
                if (result.isSuccess) {
                    Result.success(outputFile)
                } else {
                    if (outputFile.exists()) outputFile.delete()
                    Result.failure(result.exceptionOrNull() ?: Exception("Failed to merge PDFs"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun splitPdf(
        inputUri: Uri,
        baseFileName: String,
        splitMode: SplitMode,
        pageGroups: List<List<Int>>
    ): Result<List<SplitOutputFile>> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "split_pdfs_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val cleanName = if (baseFileName.endsWith(".pdf", ignoreCase = true)) {
                baseFileName.substring(0, baseFileName.length - 4)
            } else {
                baseFileName
            }

            val targetFiles = mutableListOf<File>()
            val targetNames = mutableListOf<String>()
            val streamProviders = mutableListOf<() -> OutputStream>()

            for ((index, _) in pageGroups.withIndex()) {
                val name = when (splitMode) {
                    is SplitMode.EveryPage -> "${cleanName}_page_${String.format(Locale.US, "%03d", index + 1)}.pdf"
                    is SplitMode.ByRanges -> "${cleanName}_part_${String.format(Locale.US, "%03d", index + 1)}.pdf"
                    is SplitMode.ExtractPages -> "${cleanName}_extracted.pdf"
                }
                val file = File(outputDir, name)
                targetFiles.add(file)
                targetNames.add(name)
                streamProviders.add { FileOutputStream(file) }
            }

            val result = pdfEngine.splitPdf(
                contentResolver = context.contentResolver,
                inputUri = inputUri,
                pageGroups = pageGroups,
                outputStreamProviders = streamProviders
            )

            if (result.isSuccess) {
                val pageCounts = result.getOrThrow()
                val authority = "${context.packageName}.fileprovider"
                val outputList = mutableListOf<SplitOutputFile>()

                for (i in targetFiles.indices) {
                    val file = targetFiles[i]
                    val contentUri = try {
                        FileProvider.getUriForFile(context, authority, file)
                    } catch (e: Exception) {
                        Uri.fromFile(file)
                    }
                    outputList.add(
                        SplitOutputFile(
                            file = file,
                            uri = contentUri,
                            displayName = targetNames[i],
                            size = file.length(),
                            pageCount = pageCounts[i]
                        )
                    )
                }
                Result.success(outputList)
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to split PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun organizePdf(
        inputUri: Uri,
        baseFileName: String,
        pageSpecs: List<OrganizePageSpec>
    ): Result<OrganizeOutputFile> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "organized_pdfs_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val cleanName = if (baseFileName.endsWith(".pdf", ignoreCase = true)) {
                baseFileName.substring(0, baseFileName.length - 4)
            } else {
                baseFileName
            }

            val targetName = "${cleanName}_organized.pdf"
            val targetFile = File(outputDir, targetName)

            val result = FileOutputStream(targetFile).use { outStream ->
                pdfEngine.organizePdf(
                    contentResolver = context.contentResolver,
                    inputUri = inputUri,
                    pageSpecs = pageSpecs,
                    outputStream = outStream
                )
            }

            if (result.isSuccess) {
                val pageCount = result.getOrThrow()
                val authority = "${context.packageName}.fileprovider"
                val contentUri = try {
                    FileProvider.getUriForFile(context, authority, targetFile)
                } catch (e: Exception) {
                    Uri.fromFile(targetFile)
                }
                val outputFile = OrganizeOutputFile(
                    file = targetFile,
                    uri = contentUri,
                    displayName = targetName,
                    size = targetFile.length(),
                    pageCount = pageCount
                )
                Result.success(outputFile)
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to organize PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun compressPdf(
        inputUri: Uri,
        baseFileName: String,
        level: CompressionLevel
    ): Result<CompressPdfResult> = withContext(ioDispatcher) {
        try {
            val fileInfo = getPdfFileInfo(inputUri)
            val originalSize = fileInfo.size

            val outputDir = File(context.cacheDir, "compressed_pdfs_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val cleanName = if (baseFileName.endsWith(".pdf", ignoreCase = true)) {
                baseFileName.substring(0, baseFileName.length - 4)
            } else {
                baseFileName
            }

            val targetName = "${cleanName}_compressed.pdf"
            val targetFile = File(outputDir, targetName)

            val result = FileOutputStream(targetFile).use { outStream ->
                pdfEngine.compressPdf(
                    contentResolver = context.contentResolver,
                    inputUri = inputUri,
                    level = level,
                    outputStream = outStream
                )
            }

            if (result.isSuccess) {
                val pageCount = result.getOrThrow()
                val compressedSize = targetFile.length()
                val authority = "${context.packageName}.fileprovider"

                if (originalSize > 0 && compressedSize >= originalSize) {
                    targetFile.delete()
                    outputDir.deleteRecursively()
                    Result.success(
                        CompressPdfResult.AlreadyOptimized(
                            file = targetFile,
                            uri = inputUri,
                            displayName = baseFileName,
                            originalSize = originalSize,
                            pageCount = pageCount
                        )
                    )
                } else {
                    val contentUri = try {
                        FileProvider.getUriForFile(context, authority, targetFile)
                    } catch (e: Exception) {
                        Uri.fromFile(targetFile)
                    }

                    Result.success(
                        CompressPdfResult.Success(
                            file = targetFile,
                            uri = contentUri,
                            displayName = targetName,
                            originalSize = originalSize,
                            compressedSize = compressedSize,
                            pageCount = pageCount
                        )
                    )
                }
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to compress PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun convertImagesToPdf(
        images: List<ImageSpec>,
        baseFileName: String,
        pageSize: ImageToPdfPageSize,
        quality: ImageToPdfQuality,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<ImageToPdfResult> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "created_pdfs_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val cleanName = if (baseFileName.endsWith(".pdf", ignoreCase = true)) {
                baseFileName
            } else {
                "$baseFileName.pdf"
            }

            val targetFile = File(outputDir, cleanName)

            val result = FileOutputStream(targetFile).use { outStream ->
                pdfEngine.convertImagesToPdf(
                    contentResolver = context.contentResolver,
                    images = images,
                    pageSize = pageSize,
                    quality = quality,
                    outputStream = outStream,
                    onProgress = onProgress
                )
            }

            if (result.isSuccess) {
                val pageCount = result.getOrThrow()
                val authority = "${context.packageName}.fileprovider"
                val contentUri = try {
                    FileProvider.getUriForFile(context, authority, targetFile)
                } catch (e: Exception) {
                    Uri.fromFile(targetFile)
                }
                val outputFile = ImageToPdfResult(
                    file = targetFile,
                    uri = contentUri,
                    displayName = cleanName,
                    size = targetFile.length(),
                    pageCount = pageCount
                )
                Result.success(outputFile)
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to create PDF from images"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun convertPdfToImages(
        params: PdfToImageParams,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<PdfToImageResult> = withContext(ioDispatcher) {
        try {
            val fileInfo = getPdfFileInfo(params.inputUri)
            val totalInputPages = fileInfo.pageCount ?: params.pagesToRender.size

            val outputDir = File(context.cacheDir, "pdf_images_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val rawBaseName = params.baseFileName
            val cleanName = if (rawBaseName.endsWith(".pdf", ignoreCase = true)) {
                rawBaseName.substring(0, rawBaseName.length - 4)
            } else {
                rawBaseName
            }

            val extension = params.format.extension
            val targetFiles = mutableListOf<File>()
            val targetNames = mutableListOf<String>()
            val streamProviders = mutableListOf<() -> OutputStream>()

            for (pageIdx in params.pagesToRender) {
                val fileName = "${cleanName}_page_${String.format(Locale.US, "%03d", pageIdx + 1)}.$extension"
                val file = File(outputDir, fileName)
                targetFiles.add(file)
                targetNames.add(fileName)
                streamProviders.add { FileOutputStream(file) }
            }

            val result = pdfEngine.convertPdfToImages(
                contentResolver = context.contentResolver,
                inputUri = params.inputUri,
                format = params.format,
                quality = params.quality,
                resolution = params.resolution,
                pagesToRender = params.pagesToRender,
                outputStreamProviders = streamProviders,
                onProgress = onProgress
            )

            if (result.isSuccess) {
                val authority = "${context.packageName}.fileprovider"
                val outputFiles = mutableListOf<PdfToImageOutputFile>()

                for (i in targetFiles.indices) {
                    val file = targetFiles[i]
                    val contentUri = try {
                        FileProvider.getUriForFile(context, authority, file)
                    } catch (e: Exception) {
                        Uri.fromFile(file)
                    }
                    outputFiles.add(
                        PdfToImageOutputFile(
                            file = file,
                            uri = contentUri,
                            displayName = targetNames[i],
                            size = file.length(),
                            pageIndex = params.pagesToRender[i]
                        )
                    )
                }
                Result.success(
                    PdfToImageResult(
                        outputFiles = outputFiles,
                        totalInputPages = totalInputPages
                    )
                )
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to convert PDF to images"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun protectPdf(params: ProtectPdfParams): Result<ProtectUnlockOutputFile> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "protected_pdfs_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val rawBaseName = params.baseFileName
            val cleanName = if (rawBaseName.endsWith(".pdf", ignoreCase = true)) {
                rawBaseName.substring(0, rawBaseName.length - 4)
            } else {
                rawBaseName
            }

            val targetName = "${cleanName}_protected.pdf"
            val targetFile = File(outputDir, targetName)

            val result = FileOutputStream(targetFile).use { outStream ->
                pdfEngine.protectPdf(
                    contentResolver = context.contentResolver,
                    inputUri = params.inputUri,
                    password = params.password,
                    preventPrinting = params.preventPrinting,
                    preventCopying = params.preventCopying,
                    preventModifying = params.preventModifying,
                    outputStream = outStream
                )
            }

            if (result.isSuccess) {
                val pageCount = result.getOrThrow()
                val authority = "${context.packageName}.fileprovider"
                val contentUri = try {
                    FileProvider.getUriForFile(context, authority, targetFile)
                } catch (e: Exception) {
                    Uri.fromFile(targetFile)
                }
                val outputFile = ProtectUnlockOutputFile(
                    file = targetFile,
                    uri = contentUri,
                    displayName = targetName,
                    size = targetFile.length(),
                    pageCount = pageCount,
                    isUnlocked = false
                )
                Result.success(outputFile)
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to protect PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlockPdf(params: UnlockPdfParams): Result<ProtectUnlockOutputFile> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "unlocked_pdfs_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val rawBaseName = params.baseFileName
            val cleanName = if (rawBaseName.endsWith(".pdf", ignoreCase = true)) {
                val base = rawBaseName.substring(0, rawBaseName.length - 4)
                if (base.endsWith("_protected", ignoreCase = true)) {
                    base.substring(0, base.length - 10)
                } else {
                    base
                }
            } else {
                rawBaseName
            }

            val targetName = "${cleanName}_unlocked.pdf"
            val targetFile = File(outputDir, targetName)

            val result = FileOutputStream(targetFile).use { outStream ->
                pdfEngine.unlockPdf(
                    contentResolver = context.contentResolver,
                    inputUri = params.inputUri,
                    password = params.password,
                    outputStream = outStream
                )
            }

            if (result.isSuccess) {
                val pageCount = result.getOrThrow()
                val authority = "${context.packageName}.fileprovider"
                val contentUri = try {
                    FileProvider.getUriForFile(context, authority, targetFile)
                } catch (e: Exception) {
                    Uri.fromFile(targetFile)
                }
                val outputFile = ProtectUnlockOutputFile(
                    file = targetFile,
                    uri = contentUri,
                    displayName = targetName,
                    size = targetFile.length(),
                    pageCount = pageCount,
                    isUnlocked = true
                )
                Result.success(outputFile)
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to unlock PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signPdf(params: SignPdfParams): Result<SignOutputFile> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "signed_pdfs_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val rawBaseName = params.baseFileName
            val cleanName = if (rawBaseName.endsWith(".pdf", ignoreCase = true)) {
                rawBaseName.substring(0, rawBaseName.length - 4)
            } else {
                rawBaseName
            }

            val targetName = "${cleanName}_signed.pdf"
            val targetFile = File(outputDir, targetName)

            val result = FileOutputStream(targetFile).use { outStream ->
                pdfEngine.signPdf(
                    contentResolver = context.contentResolver,
                    inputUri = params.inputUri,
                    signatures = params.signatures,
                    outputStream = outStream
                )
            }

            if (result.isSuccess) {
                val pageCount = result.getOrThrow()
                val authority = "${context.packageName}.fileprovider"
                val contentUri = try {
                    FileProvider.getUriForFile(context, authority, targetFile)
                } catch (e: Exception) {
                    Uri.fromFile(targetFile)
                }
                val outputFile = SignOutputFile(
                    file = targetFile,
                    uri = contentUri,
                    displayName = targetName,
                    size = targetFile.length(),
                    pageCount = pageCount
                )
                Result.success(outputFile)
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to sign PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun convertWordToPdf(
        inputUri: Uri,
        baseFileName: String,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<WordToPdfResult> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "word_pdfs_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val cleanName = when {
                baseFileName.endsWith(".docx", ignoreCase = true) -> baseFileName.substring(0, baseFileName.length - 5)
                baseFileName.endsWith(".doc", ignoreCase = true) -> baseFileName.substring(0, baseFileName.length - 4)
                baseFileName.endsWith(".pdf", ignoreCase = true) -> baseFileName.substring(0, baseFileName.length - 4)
                else -> baseFileName
            }

            val targetName = "$cleanName.pdf"
            val targetFile = File(outputDir, targetName)

            val result = FileOutputStream(targetFile).use { outStream ->
                pdfEngine.convertWordToPdf(
                    contentResolver = context.contentResolver,
                    inputUri = inputUri,
                    outputStream = outStream,
                    onProgress = onProgress
                )
            }

            if (result.isSuccess) {
                val pageCount = result.getOrThrow()
                val authority = "${context.packageName}.fileprovider"
                val contentUri = try {
                    FileProvider.getUriForFile(context, authority, targetFile)
                } catch (e: Exception) {
                    Uri.fromFile(targetFile)
                }
                val outputFile = WordToPdfResult(
                    file = targetFile,
                    uri = contentUri,
                    displayName = targetName,
                    size = targetFile.length(),
                    pageCount = pageCount
                )
                Result.success(outputFile)
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to convert Word document to PDF"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun convertPdfToWord(
        inputUri: Uri,
        baseFileName: String,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): Result<PdfToWordResult> = withContext(ioDispatcher) {
        try {
            val outputDir = File(context.cacheDir, "pdf_words_${System.currentTimeMillis()}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val cleanName = if (baseFileName.endsWith(".pdf", ignoreCase = true)) {
                baseFileName.substring(0, baseFileName.length - 4)
            } else {
                baseFileName
            }

            var targetName = "$cleanName.docx"
            var targetFile = File(outputDir, targetName)
            var counter = 1
            while (targetFile.exists()) {
                targetName = "$cleanName ($counter).docx"
                targetFile = File(outputDir, targetName)
                counter++
            }

            val result = FileOutputStream(targetFile).use { outStream ->
                pdfEngine.convertPdfToWord(
                    contentResolver = context.contentResolver,
                    inputUri = inputUri,
                    outputStream = outStream,
                    onProgress = onProgress
                )
            }

            if (result.isSuccess) {
                val pageCount = result.getOrThrow()
                val authority = "${context.packageName}.fileprovider"
                val contentUri = try {
                    FileProvider.getUriForFile(context, authority, targetFile)
                } catch (e: Exception) {
                    Uri.fromFile(targetFile)
                }
                val outputFile = PdfToWordResult(
                    file = targetFile,
                    uri = contentUri,
                    displayName = targetName,
                    size = targetFile.length(),
                    pageCount = pageCount
                )
                Result.success(outputFile)
            } else {
                outputDir.deleteRecursively()
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to convert PDF to Word"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readMetaData(context: Context, uri: Uri): PdfFile {
        var displayName = "document.pdf"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        if (size == 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    size = afd.length
                }
            } catch (ignored: Exception) {
            }
        }

        return PdfFile(
            uri = uri,
            displayName = displayName,
            size = size
        )
    }
}
