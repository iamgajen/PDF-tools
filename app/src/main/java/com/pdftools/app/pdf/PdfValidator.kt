package com.pdftools.app.pdf

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.InputStream

class PdfValidator {

    fun validate(inputStreamProvider: () -> InputStream?): PdfValidationResult {
        return try {
            val inputStream = inputStreamProvider()
                ?: return PdfValidationResult.Unreadable("Unable to access this file.")

            inputStream.use { stream ->
                try {
                    val document = PDDocument.load(stream)
                    document.use { doc ->
                        if (doc.isEncrypted) {
                            PdfValidationResult.Encrypted("This PDF is password protected.")
                        } else {
                            PdfValidationResult.Valid(doc.numberOfPages)
                        }
                    }
                } catch (e: InvalidPasswordException) {
                    PdfValidationResult.Encrypted("This PDF is password protected.")
                } catch (e: Exception) {
                    PdfValidationResult.Invalid("Unable to read this PDF.")
                }
            }
        } catch (e: SecurityException) {
            PdfValidationResult.Unreadable("Unable to access this file.")
        } catch (e: Exception) {
            PdfValidationResult.Invalid("Unable to read this PDF.")
        }
    }

    fun validate(contentResolver: ContentResolver, uri: Uri): PdfValidationResult {
        return validate {
            contentResolver.openInputStream(uri)
        }
    }
}
