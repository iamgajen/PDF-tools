package com.pdftools.app.ui.pdftoimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdftools.app.data.pdf.PdfRepository
import com.pdftools.app.data.pdf.PdfRepositoryImpl
import com.pdftools.app.domain.pdf.PageRangeParseResult
import com.pdftools.app.domain.pdf.PageRangeParser
import com.pdftools.app.domain.pdf.PdfFile
import com.pdftools.app.domain.pdf.PdfToImageFormat
import com.pdftools.app.domain.pdf.PdfToImagePageSelectionMode
import com.pdftools.app.domain.pdf.PdfToImageParams
import com.pdftools.app.domain.pdf.PdfToImageQuality
import com.pdftools.app.domain.pdf.PdfToImageResolution
import com.pdftools.app.domain.pdf.PdfToImageResult
import com.pdftools.app.domain.pdf.PdfToImageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PdfToImageUiState {
    object Idle : PdfToImageUiState
    object Loading : PdfToImageUiState
    data class Rendering(val current: Int, val total: Int) : PdfToImageUiState
    data class Success(val result: PdfToImageResult) : PdfToImageUiState
    data class Error(val message: String) : PdfToImageUiState
}

class PdfToImageViewModel(
    private val pdfRepository: PdfRepository,
    private val pdfToImageUseCase: PdfToImageUseCase = PdfToImageUseCase(pdfRepository)
) : ViewModel() {

    private val _selectedPdfFile = MutableStateFlow<PdfFile?>(null)
    val selectedPdfFile: StateFlow<PdfFile?> = _selectedPdfFile.asStateFlow()

    private val _selectedFormat = MutableStateFlow(PdfToImageFormat.JPG)
    val selectedFormat: StateFlow<PdfToImageFormat> = _selectedFormat.asStateFlow()

    private val _selectedQuality = MutableStateFlow(PdfToImageQuality.HIGH)
    val selectedQuality: StateFlow<PdfToImageQuality> = _selectedQuality.asStateFlow()

    private val _selectedResolution = MutableStateFlow(PdfToImageResolution.DPI_200)
    val selectedResolution: StateFlow<PdfToImageResolution> = _selectedResolution.asStateFlow()

    private val _selectedPageSelectionMode = MutableStateFlow(PdfToImagePageSelectionMode.ALL_PAGES)
    val selectedPageSelectionMode: StateFlow<PdfToImagePageSelectionMode> = _selectedPageSelectionMode.asStateFlow()

    private val _rangeInput = MutableStateFlow("")
    val rangeInput: StateFlow<String> = _rangeInput.asStateFlow()

    private val _selectedPagesSet = MutableStateFlow<Set<Int>>(emptySet())
    val selectedPagesSet: StateFlow<Set<Int>> = _selectedPagesSet.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _uiState = MutableStateFlow<PdfToImageUiState>(PdfToImageUiState.Idle)
    val uiState: StateFlow<PdfToImageUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _pageThumbnails = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageThumbnails: StateFlow<Map<Int, Bitmap>> = _pageThumbnails.asStateFlow()

    private var conversionJob: Job? = null

    fun setPdfUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = PdfToImageUiState.Loading
            val fileInfo = pdfRepository.getPdfFileInfo(uri)
            if (!fileInfo.isValid) {
                _userMessage.value = fileInfo.errorMessage ?: "Unable to read this PDF file."
                _selectedPdfFile.value = null
                _uiState.value = PdfToImageUiState.Idle
                return@launch
            }

            val pageCount = fileInfo.pageCount ?: 0
            if (pageCount <= 0) {
                _userMessage.value = "Selected PDF has no readable pages."
                _selectedPdfFile.value = null
                _uiState.value = PdfToImageUiState.Idle
                return@launch
            }

            _selectedPdfFile.value = fileInfo
            _selectedFormat.value = PdfToImageFormat.JPG
            _selectedQuality.value = PdfToImageQuality.HIGH
            _selectedResolution.value = PdfToImageResolution.DPI_200
            _selectedPageSelectionMode.value = PdfToImagePageSelectionMode.ALL_PAGES
            _rangeInput.value = if (pageCount > 1) "1-${minOf(5, pageCount)}" else "1"
            _selectedPagesSet.value = (0 until pageCount).toSet()
            _validationError.value = null
            _uiState.value = PdfToImageUiState.Idle
            _pageThumbnails.value = emptyMap()

            loadThumbnails(context, uri, pageCount)
        }
    }

    fun setFormat(format: PdfToImageFormat) {
        _selectedFormat.value = format
    }

    fun setQuality(quality: PdfToImageQuality) {
        _selectedQuality.value = quality
    }

    fun setResolution(resolution: PdfToImageResolution) {
        _selectedResolution.value = resolution
    }

    fun setPageSelectionMode(mode: PdfToImagePageSelectionMode) {
        _selectedPageSelectionMode.value = mode
        validateCurrentInput()
    }

    fun updateRangeInput(input: String) {
        _rangeInput.value = input
        validateCurrentInput()
    }

    fun togglePageSelection(pageIndex: Int) {
        val currentSet = _selectedPagesSet.value.toMutableSet()
        if (currentSet.contains(pageIndex)) {
            currentSet.remove(pageIndex)
        } else {
            currentSet.add(pageIndex)
        }
        _selectedPagesSet.value = currentSet
        _selectedPageSelectionMode.value = PdfToImagePageSelectionMode.SELECTED_PAGES
        validateCurrentInput()
    }

    fun selectAllPages() {
        val pageCount = _selectedPdfFile.value?.pageCount ?: 0
        if (pageCount > 0) {
            _selectedPagesSet.value = (0 until pageCount).toSet()
            _selectedPageSelectionMode.value = PdfToImagePageSelectionMode.ALL_PAGES
            validateCurrentInput()
        }
    }

    fun clearPageSelection() {
        _selectedPagesSet.value = emptySet()
        _selectedPageSelectionMode.value = PdfToImagePageSelectionMode.SELECTED_PAGES
        validateCurrentInput()
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorAndKeepState() {
        _uiState.value = PdfToImageUiState.Idle
    }

    fun reset() {
        conversionJob?.cancel()
        _uiState.value = PdfToImageUiState.Idle
        _selectedPdfFile.value = null
        _selectedFormat.value = PdfToImageFormat.JPG
        _selectedQuality.value = PdfToImageQuality.HIGH
        _selectedResolution.value = PdfToImageResolution.DPI_200
        _selectedPageSelectionMode.value = PdfToImagePageSelectionMode.ALL_PAGES
        _rangeInput.value = ""
        _selectedPagesSet.value = emptySet()
        _validationError.value = null
        _pageThumbnails.value = emptyMap()
        _userMessage.value = null
    }

    fun getPagesToRender(): List<Int> {
        val file = _selectedPdfFile.value ?: return emptyList()
        val totalPages = file.pageCount ?: 0
        if (totalPages <= 0) return emptyList()

        return when (_selectedPageSelectionMode.value) {
            PdfToImagePageSelectionMode.ALL_PAGES -> (0 until totalPages).toList()
            PdfToImagePageSelectionMode.SELECTED_PAGES -> _selectedPagesSet.value.sorted()
            PdfToImagePageSelectionMode.PAGE_RANGE -> {
                when (val parseResult = PageRangeParser.parseRanges(_rangeInput.value, totalPages)) {
                    is PageRangeParseResult.Success -> parseResult.pageGroups.flatten().distinct().sorted()
                    is PageRangeParseResult.Error -> emptyList()
                }
            }
        }
    }

    fun convertPdfToImages() {
        val file = _selectedPdfFile.value ?: run {
            _userMessage.value = "No PDF file selected."
            return
        }

        val pages = getPagesToRender()
        if (pages.isEmpty()) {
            val err = when (_selectedPageSelectionMode.value) {
                PdfToImagePageSelectionMode.PAGE_RANGE -> _validationError.value ?: "Invalid page range."
                else -> "Please select at least one page to convert."
            }
            _validationError.value = err
            _userMessage.value = err
            return
        }

        _validationError.value = null
        _uiState.value = PdfToImageUiState.Rendering(1, pages.size)

        conversionJob = viewModelScope.launch(Dispatchers.IO) {
            val params = PdfToImageParams(
                inputUri = file.uri,
                baseFileName = file.displayName,
                format = _selectedFormat.value,
                quality = _selectedQuality.value,
                resolution = _selectedResolution.value,
                pagesToRender = pages
            )

            val result = pdfToImageUseCase(params) { current, total ->
                _uiState.value = PdfToImageUiState.Rendering(current, total)
            }

            if (result.isSuccess) {
                _uiState.value = PdfToImageUiState.Success(result.getOrThrow())
            } else {
                val exception = result.exceptionOrNull()
                if (exception is CancellationException) {
                    _uiState.value = PdfToImageUiState.Idle
                } else {
                    val errorMsg = exception?.localizedMessage ?: "Failed to convert PDF to images."
                    _uiState.value = PdfToImageUiState.Error(errorMsg)
                }
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _uiState.value = PdfToImageUiState.Idle
    }

    private fun validateCurrentInput() {
        val totalPages = _selectedPdfFile.value?.pageCount ?: 0
        if (totalPages <= 0) return

        when (_selectedPageSelectionMode.value) {
            PdfToImagePageSelectionMode.ALL_PAGES -> {
                _validationError.value = null
            }
            PdfToImagePageSelectionMode.SELECTED_PAGES -> {
                if (_selectedPagesSet.value.isEmpty()) {
                    _validationError.value = "No pages selected."
                } else {
                    _validationError.value = null
                }
            }
            PdfToImagePageSelectionMode.PAGE_RANGE -> {
                when (val parseResult = PageRangeParser.parseRanges(_rangeInput.value, totalPages)) {
                    is PageRangeParseResult.Error -> _validationError.value = parseResult.message
                    is PageRangeParseResult.Success -> _validationError.value = null
                }
            }
        }
    }

    private fun loadThumbnails(context: Context, uri: Uri, pageCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            val map = mutableMapOf<Int, Bitmap>()
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                renderer = PdfRenderer(pfd)
                val count = minOf(pageCount, renderer.pageCount)
                for (i in 0 until count) {
                    try {
                        val page = renderer.openPage(i)
                        val targetWidth = 200
                        val scale = targetWidth.toFloat() / page.width.toFloat()
                        val w = targetWidth
                        val h = (page.height * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        map[i] = bitmap
                        _pageThumbnails.value = map.toMap()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = PdfRepositoryImpl(context.applicationContext)
            val useCase = PdfToImageUseCase(repo)
            return PdfToImageViewModel(repo, useCase) as T
        }
    }
}
