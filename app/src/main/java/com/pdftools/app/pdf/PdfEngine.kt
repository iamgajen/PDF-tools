package com.pdftools.app.pdf

import android.content.ContentResolver
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
import java.io.InputStream
import java.io.OutputStream

interface PdfEngine {
    fun validatePdf(contentResolver: ContentResolver, uri: Uri): PdfValidationResult
    fun validatePdfStream(inputStreamProvider: () -> InputStream?): PdfValidationResult
    fun mergePdfs(contentResolver: ContentResolver, inputUris: List<Uri>, outputStream: OutputStream): Result<Int>
    fun mergePdfStreams(streamProviders: List<() -> InputStream?>, outputStream: OutputStream): Result<Int>
    fun splitPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        pageGroups: List<List<Int>>,
        outputStreamProviders: List<() -> OutputStream>
    ): Result<List<Int>>
    fun splitPdfStreams(
        inputStreamProvider: () -> InputStream?,
        pageGroups: List<List<Int>>,
        outputStreamProviders: List<() -> OutputStream>
    ): Result<List<Int>>
    fun organizePdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        pageSpecs: List<OrganizePageSpec>,
        outputStream: OutputStream
    ): Result<Int>
    fun organizePdfStreams(
        inputStreamProvider: () -> InputStream?,
        pageSpecs: List<OrganizePageSpec>,
        outputStream: OutputStream
    ): Result<Int>
    fun compressPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        level: CompressionLevel,
        outputStream: OutputStream
    ): Result<Int>
    fun compressPdfStreams(
        inputStreamProvider: () -> InputStream?,
        level: CompressionLevel,
        outputStream: OutputStream
    ): Result<Int>
    fun convertImagesToPdf(
        contentResolver: ContentResolver,
        images: List<ImageSpec>,
        pageSize: ImageToPdfPageSize,
        quality: ImageToPdfQuality,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int>
    fun convertImagesToPdfStreams(
        imageSpecs: List<ImageStreamSpec>,
        pageSize: ImageToPdfPageSize,
        quality: ImageToPdfQuality,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int>
    fun convertPdfToImages(
        contentResolver: ContentResolver,
        inputUri: Uri,
        format: PdfToImageFormat,
        quality: PdfToImageQuality,
        resolution: PdfToImageResolution,
        pagesToRender: List<Int>,
        outputStreamProviders: List<() -> OutputStream>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int>
    fun convertPdfToImageStreams(
        inputStreamProvider: () -> InputStream?,
        fileDescriptorProvider: (() -> ParcelFileDescriptor?)? = null,
        format: PdfToImageFormat,
        quality: PdfToImageQuality,
        resolution: PdfToImageResolution,
        pagesToRender: List<Int>,
        outputStreamProviders: List<() -> OutputStream>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int>

    fun protectPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        password: String,
        preventPrinting: Boolean,
        preventCopying: Boolean,
        preventModifying: Boolean,
        outputStream: OutputStream
    ): Result<Int>

    fun protectPdfStreams(
        inputStreamProvider: () -> InputStream?,
        password: String,
        preventPrinting: Boolean,
        preventCopying: Boolean,
        preventModifying: Boolean,
        outputStream: OutputStream
    ): Result<Int>

    fun unlockPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        password: String,
        outputStream: OutputStream
    ): Result<Int>

    fun unlockPdfStreams(
        inputStreamProvider: () -> InputStream?,
        password: String,
        outputStream: OutputStream
    ): Result<Int>

    fun signPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        signatures: List<SignatureSpec>,
        outputStream: OutputStream
    ): Result<Int>

    fun signPdfStreams(
        inputStreamProvider: () -> InputStream?,
        signatures: List<SignatureSpec>,
        outputStream: OutputStream
    ): Result<Int>

    fun convertWordToPdf(
        contentResolver: ContentResolver,
        inputUri: Uri,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int>

    fun convertWordToPdfStreams(
        inputStreamProvider: () -> InputStream?,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int>

    fun convertPdfToWord(
        contentResolver: ContentResolver,
        inputUri: Uri,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int>

    fun convertPdfToWordStreams(
        inputStreamProvider: () -> InputStream?,
        outputStream: OutputStream,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int>
}
