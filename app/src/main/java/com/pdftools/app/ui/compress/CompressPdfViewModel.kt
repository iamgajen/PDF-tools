package com.pdftools.app.ui.compress

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdftools.app.data.pdf.PdfRepository
import com.pdftools.app.data.pdf.PdfRepositoryImpl
import com.pdftools.app.domain.pdf.CompressPdfResult
import com.pdftools.app.domain.pdf.CompressPdfUseCase
import com.pdftools.app.domain.pdf.CompressionLevel
import com.pdftools.app.domain.pdf.PdfFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CompressUiState {
    object Idle : CompressUiState
    object Processing : CompressUiState
    data class Success(val result: CompressPdfResult.Success) : CompressUiState
    data class AlreadyOptimized(val result: CompressPdfResult.AlreadyOptimized) : CompressUiState
    data class Error(val message: String) : CompressUiState
}

class CompressPdfViewModel(
    private val pdfRepository: PdfRepository,
    private val compressPdfUseCase: CompressPdfUseCase = CompressPdfUseCase(pdfRepository)
) : ViewModel() {

    private val _selectedPdfFile = MutableStateFlow<PdfFile?>(null)
    val selectedPdfFile: StateFlow<PdfFile?> = _selectedPdfFile.asStateFlow()

    private val _selectedLevel = MutableStateFlow(CompressionLevel.BALANCED)
    val selectedLevel: StateFlow<CompressionLevel> = _selectedLevel.asStateFlow()

    private val _uiState = MutableStateFlow<CompressUiState>(CompressUiState.Idle)
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun setPdfUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val fileInfo = pdfRepository.getPdfFileInfo(uri)
            if (!fileInfo.isValid) {
                _userMessage.value = fileInfo.errorMessage ?: "Unable to read this PDF file."
                _selectedPdfFile.value = null
                return@launch
            }

            val pageCount = fileInfo.pageCount ?: 0
            if (pageCount <= 0) {
                _userMessage.value = "Selected PDF has no readable pages."
                _selectedPdfFile.value = null
                return@launch
            }

            _selectedPdfFile.value = fileInfo
            _selectedLevel.value = CompressionLevel.BALANCED
            _uiState.value = CompressUiState.Idle
        }
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _selectedLevel.value = level
    }

    fun compressPdf() {
        val pdfFile = _selectedPdfFile.value ?: return
        val level = _selectedLevel.value

        viewModelScope.launch {
            _uiState.value = CompressUiState.Processing
            try {
                val result = compressPdfUseCase(
                    inputUri = pdfFile.uri,
                    baseFileName = pdfFile.displayName,
                    level = level
                )
                result.fold(
                    onSuccess = { compressResult ->
                        when (compressResult) {
                            is CompressPdfResult.Success -> {
                                _uiState.value = CompressUiState.Success(compressResult)
                            }
                            is CompressPdfResult.AlreadyOptimized -> {
                                _uiState.value = CompressUiState.AlreadyOptimized(compressResult)
                            }
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = CompressUiState.Error(
                            error.localizedMessage ?: "Failed to compress PDF."
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = CompressUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred."
                )
            }
        }
    }

    fun clearPdfSelection() {
        _selectedPdfFile.value = null
        _uiState.value = CompressUiState.Idle
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorAndKeepState() {
        _uiState.value = CompressUiState.Idle
    }

    fun reset() {
        _uiState.value = CompressUiState.Idle
        _selectedPdfFile.value = null
        _selectedLevel.value = CompressionLevel.BALANCED
        _userMessage.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = PdfRepositoryImpl(context.applicationContext)
            return CompressPdfViewModel(repository) as T
        }
    }
}
