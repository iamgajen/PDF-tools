package com.pdftools.app.domain.pdf

sealed interface PdfOperationResult<out T> {
    data class Success<T>(val data: T) : PdfOperationResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : PdfOperationResult<Nothing>
}
