package com.pdftools.app.data.pdf

import android.net.Uri
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
import com.pdftools.app.domain.pdf.PdfToImageParams
import com.pdftools.app.domain.pdf.PdfToImageResult
import com.pdftools.app.domain.pdf.ProtectPdfParams
import com.pdftools.app.domain.pdf.ProtectUnlockOutputFile
import com.pdftools.app.domain.pdf.SignOutputFile
import com.pdftools.app.domain.pdf.SignPdfParams
import com.pdftools.app.domain.pdf.UnlockPdfParams
import com.pdftools.app.domain.pdf.WordToPdfResult
import com.pdftools.app.domain.pdf.PdfToWordResult
import java.io.File
import java.io.OutputStream

interface PdfRepository {
    suspend fun getPdfFileInfo(uri: Uri): PdfFile
    suspend fun mergePdfs(inputUris: List<Uri>, outputStream: OutputStream): Result<Int>
    suspend fun createTempMergedFile(inputUris: List<Uri>): Result<File>

    suspend fun splitPdf(
        inputUri: Uri,
        baseFileName: String,
        splitMode: SplitMode,
        pageGroups: List<List<Int>>
    ): Result<List<SplitOutputFile>>

    suspend fun organizePdf(
        inputUri: Uri,
        baseFileName: String,
        pageSpecs: List<OrganizePageSpec>
    ): Result<OrganizeOutputFile>

    suspend fun compressPdf(
        inputUri: Uri,
        baseFileName: String,
        level: CompressionLevel
    ): Result<CompressPdfResult>

    suspend fun convertImagesToPdf(
        images: List<ImageSpec>,
        baseFileName: String,
        pageSize: ImageToPdfPageSize,
        quality: ImageToPdfQuality,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<ImageToPdfResult> = Result.failure(NotImplementedError("convertImagesToPdf not implemented"))

    suspend fun convertPdfToImages(
        params: PdfToImageParams,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<PdfToImageResult> = Result.failure(NotImplementedError("convertPdfToImages not implemented"))

    suspend fun protectPdf(params: ProtectPdfParams): Result<ProtectUnlockOutputFile> = Result.failure(NotImplementedError("protectPdf not implemented"))

    suspend fun unlockPdf(params: UnlockPdfParams): Result<ProtectUnlockOutputFile> = Result.failure(NotImplementedError("unlockPdf not implemented"))

    suspend fun signPdf(params: SignPdfParams): Result<SignOutputFile> = Result.failure(NotImplementedError("signPdf not implemented"))

    suspend fun convertWordToPdf(
        inputUri: Uri,
        baseFileName: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<WordToPdfResult> = Result.failure(NotImplementedError("convertWordToPdf not implemented"))

    suspend fun convertPdfToWord(
        inputUri: Uri,
        baseFileName: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<PdfToWordResult> = Result.failure(NotImplementedError("convertPdfToWord not implemented"))
}
