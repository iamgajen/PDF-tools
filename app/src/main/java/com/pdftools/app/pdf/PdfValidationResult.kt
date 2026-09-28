package com.pdftools.app.pdf

sealed interface PdfValidationResult {
    data class Valid(val pageCount: Int) : PdfValidationResult
    data class Encrypted(val message: String = "This PDF is password protected.") : PdfValidationResult
    data class Invalid(val message: String = "Unable to read this PDF.") : PdfValidationResult
    data class Unreadable(val message: String = "Unable to access this file.") : PdfValidationResult
}
