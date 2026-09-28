package com.pdftools.app.ui.merge

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdftools.app.data.pdf.PdfRepository
import com.pdftools.app.data.pdf.PdfRepositoryImpl
import com.pdftools.app.domain.pdf.MergePdfUseCase
import com.pdftools.app.domain.pdf.PdfFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream

sealed interface MergeUiState {
    object Idle : MergeUiState
    object Processing : MergeUiState
    data class Success(
        val outputUri: Uri,
        val fileName: String,
        val fileSize: String,
        val pageCount: Int
    ) : MergeUiState
    data class Error(val message: String) : MergeUiState
}

class MergePdfViewModel(
    private val pdfRepository: PdfRepository,
    private val mergePdfUseCase: MergePdfUseCase = MergePdfUseCase(pdfRepository)
) : ViewModel() {

    private val _pdfFiles = MutableStateFlow<List<PdfFile>>(emptyList())
    val pdfFiles: StateFlow<List<PdfFile>> = _pdfFiles.asStateFlow()

    private val _uiState = MutableStateFlow<MergeUiState>(MergeUiState.Idle)
    val uiState: StateFlow<MergeUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newFiles = mutableListOf<PdfFile>()
            var firstError: String? = null

            for (uri in uris) {
                // Check if already in list
                if (_pdfFiles.value.any { it.uri == uri }) continue

                val fileInfo = pdfRepository.getPdfFileInfo(uri)
                if (!fileInfo.isValid && firstError == null) {
                    firstError = fileInfo.errorMessage ?: "Unable to read this PDF."
                }
                newFiles.add(fileInfo)
            }

            if (newFiles.isNotEmpty()) {
                _pdfFiles.value = _pdfFiles.value + newFiles
            }

            if (firstError != null) {
                _userMessage.value = firstError
            }
        }
    }

    fun removeFile(id: String) {
        _pdfFiles.value = _pdfFiles.value.filterNot { it.id == id }
    }

    fun moveUp(index: Int) {
        if (index <= 0 || index >= _pdfFiles.value.size) return
        val list = _pdfFiles.value.toMutableList()
        val item = list.removeAt(index)
        list.add(index - 1, item)
        _pdfFiles.value = list
    }

    fun moveDown(index: Int) {
        if (index < 0 || index >= _pdfFiles.value.size - 1) return
        val list = _pdfFiles.value.toMutableList()
        val item = list.removeAt(index)
        list.add(index + 1, item)
        _pdfFiles.value = list
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun mergePdfsToStream(
        context: Context,
        outputUri: Uri,
        outputFileName: String,
        outputStreamProvider: () -> OutputStream?
    ) {
        val currentFiles = _pdfFiles.value
        val validFiles = currentFiles.filter { it.isValid }

        if (validFiles.size < 2) {
            _userMessage.value = "Please select at least two readable PDF files to merge."
            return
        }

        viewModelScope.launch {
            _uiState.value = MergeUiState.Processing
            try {
                val outputStream = outputStreamProvider()
                if (outputStream == null) {
                    _uiState.value = MergeUiState.Error("Unable to open output file stream.")
                    return@launch
                }

                outputStream.use { stream ->
                    val result = mergePdfUseCase(validFiles.map { it.uri }, stream)
                    result.fold(
                        onSuccess = { pageCount ->
                            val fileSize = getOutputFileSize(context, outputUri)
                            _uiState.value = MergeUiState.Success(
                                outputUri = outputUri,
                                fileName = outputFileName,
                                fileSize = PdfFile.formatFileSize(fileSize),
                                pageCount = pageCount
                            )
                        },
                        onFailure = { error ->
                            val msg = error.localizedMessage ?: "Failed to merge PDF files."
                            _uiState.value = MergeUiState.Error(msg)
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = MergeUiState.Error(e.localizedMessage ?: "Failed to save merged PDF.")
            }
        }
    }

    fun reset() {
        _uiState.value = MergeUiState.Idle
        _pdfFiles.value = emptyList()
        _userMessage.value = null
    }

    fun clearErrorAndKeepFiles() {
        _uiState.value = MergeUiState.Idle
    }

    private fun getOutputFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = PdfRepositoryImpl(context.applicationContext)
            return MergePdfViewModel(repository) as T
        }
    }
}
