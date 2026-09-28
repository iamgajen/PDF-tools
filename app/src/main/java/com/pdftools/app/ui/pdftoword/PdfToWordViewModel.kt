package com.pdftools.app.ui.pdftoword

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdftools.app.data.pdf.PdfRepository
import com.pdftools.app.data.pdf.PdfRepositoryImpl
import com.pdftools.app.domain.pdf.PdfFile
import com.pdftools.app.domain.pdf.PdfToWordResult
import com.pdftools.app.domain.pdf.PdfToWordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SelectedPdfDocument(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val pageCount: Int? = null,
    val isValid: Boolean = true,
    val isEncrypted: Boolean = false,
    val errorMessage: String? = null
) {
    val formattedSize: String
        get() = PdfFile.formatFileSize(size)
}

sealed interface PdfToWordUiState {
    object Idle : PdfToWordUiState
    data class Converting(val currentPage: Int = 1, val totalPages: Int = 1) : PdfToWordUiState
    data class Success(val result: PdfToWordResult) : PdfToWordUiState
    data class Error(val message: String) : PdfToWordUiState
}

class PdfToWordViewModel(
    private val pdfRepository: PdfRepository,
    private val pdfToWordUseCase: PdfToWordUseCase = PdfToWordUseCase(pdfRepository)
) : ViewModel() {

    private val _selectedDocument = MutableStateFlow<SelectedPdfDocument?>(null)
    val selectedDocument: StateFlow<SelectedPdfDocument?> = _selectedDocument.asStateFlow()

    private val _uiState = MutableStateFlow<PdfToWordUiState>(PdfToWordUiState.Idle)
    val uiState: StateFlow<PdfToWordUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun setPdfUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val pdfInfo = pdfRepository.getPdfFileInfo(uri)
                if (!pdfInfo.isValid) {
                    val msg = pdfInfo.errorMessage ?: if (pdfInfo.isEncrypted) {
                        "This PDF is password protected. Please unlock it first."
                    } else {
                        "The selected file is not a valid PDF."
                    }
                    _userMessage.value = msg
                    _selectedDocument.value = null
                    return@launch
                }

                _selectedDocument.value = SelectedPdfDocument(
                    uri = pdfInfo.uri,
                    displayName = pdfInfo.displayName,
                    size = pdfInfo.size,
                    pageCount = pdfInfo.pageCount,
                    isValid = true
                )
                _uiState.value = PdfToWordUiState.Idle
            } catch (e: Exception) {
                _userMessage.value = e.localizedMessage ?: "Failed to read PDF file."
                _selectedDocument.value = null
            }
        }
    }

    fun convertToWord() {
        val doc = _selectedDocument.value ?: return
        if (_uiState.value is PdfToWordUiState.Converting) return

        viewModelScope.launch {
            val totalPages = doc.pageCount ?: 1
            _uiState.value = PdfToWordUiState.Converting(currentPage = 1, totalPages = totalPages)

            try {
                val result = pdfToWordUseCase(
                    inputUri = doc.uri,
                    baseFileName = doc.displayName,
                    onProgress = { current, total ->
                        _uiState.value = PdfToWordUiState.Converting(currentPage = current, totalPages = total)
                    }
                )

                result.fold(
                    onSuccess = { wordResult ->
                        _uiState.value = PdfToWordUiState.Success(wordResult)
                    },
                    onFailure = { error ->
                        _uiState.value = PdfToWordUiState.Error(
                            error.localizedMessage ?: "Failed to convert PDF to Word document."
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = PdfToWordUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred during conversion."
                )
            }
        }
    }

    fun clearDocumentSelection() {
        _selectedDocument.value = null
        _uiState.value = PdfToWordUiState.Idle
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun reset() {
        _selectedDocument.value = null
        _uiState.value = PdfToWordUiState.Idle
        _userMessage.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = PdfRepositoryImpl(context.applicationContext)
            return PdfToWordViewModel(repository) as T
        }
    }
}
