package com.pdftools.app.ui.wordtopdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdftools.app.data.pdf.PdfRepository
import com.pdftools.app.data.pdf.PdfRepositoryImpl
import com.pdftools.app.domain.pdf.WordToPdfResult
import com.pdftools.app.domain.pdf.WordToPdfUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class SelectedWordDocument(
    val uri: Uri,
    val displayName: String,
    val size: Long
) {
    val formattedSize: String
        get() = formatFileSize(size)

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}

sealed interface WordToPdfUiState {
    object Idle : WordToPdfUiState
    data class Converting(val currentPage: Int = 0) : WordToPdfUiState
    data class Success(val result: WordToPdfResult) : WordToPdfUiState
    data class Error(val message: String) : WordToPdfUiState
}

class WordToPdfViewModel(
    private val pdfRepository: PdfRepository,
    private val wordToPdfUseCase: WordToPdfUseCase = WordToPdfUseCase(pdfRepository)
) : ViewModel() {

    private val _selectedDocument = MutableStateFlow<SelectedWordDocument?>(null)
    val selectedDocument: StateFlow<SelectedWordDocument?> = _selectedDocument.asStateFlow()

    private val _uiState = MutableStateFlow<WordToPdfUiState>(WordToPdfUiState.Idle)
    val uiState: StateFlow<WordToPdfUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun setDocumentUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val docInfo = readDocumentMetadata(context, uri)
            if (docInfo.displayName.isBlank()) {
                _userMessage.value = "Unable to read the selected file."
                _selectedDocument.value = null
                return@launch
            }

            _selectedDocument.value = docInfo
            _uiState.value = WordToPdfUiState.Idle
        }
    }

    fun convertToPdf() {
        val doc = _selectedDocument.value ?: return
        if (_uiState.value is WordToPdfUiState.Converting) return

        viewModelScope.launch {
            _uiState.value = WordToPdfUiState.Converting(currentPage = 1)
            try {
                val result = wordToPdfUseCase(
                    inputUri = doc.uri,
                    baseFileName = doc.displayName,
                    onProgress = { current, _ ->
                        _uiState.value = WordToPdfUiState.Converting(currentPage = current)
                    }
                )

                result.fold(
                    onSuccess = { wordResult ->
                        _uiState.value = WordToPdfUiState.Success(wordResult)
                    },
                    onFailure = { error ->
                        _uiState.value = WordToPdfUiState.Error(
                            error.localizedMessage ?: "Failed to convert Word document to PDF."
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = WordToPdfUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred during conversion."
                )
            }
        }
    }

    fun clearDocumentSelection() {
        _selectedDocument.value = null
        _uiState.value = WordToPdfUiState.Idle
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun reset() {
        _selectedDocument.value = null
        _uiState.value = WordToPdfUiState.Idle
        _userMessage.value = null
    }

    private fun readDocumentMetadata(context: Context, uri: Uri): SelectedWordDocument {
        var name = "Document.docx"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (ignored: Exception) {}

        if (size == 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    size = afd.length
                }
            } catch (ignored: Exception) {}
        }

        return SelectedWordDocument(
            uri = uri,
            displayName = name,
            size = size
        )
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = PdfRepositoryImpl(context.applicationContext)
            return WordToPdfViewModel(repository) as T
        }
    }
}
