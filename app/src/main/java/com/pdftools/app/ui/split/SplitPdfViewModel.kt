package com.pdftools.app.ui.split

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
import com.pdftools.app.domain.pdf.SplitMode
import com.pdftools.app.domain.pdf.SplitOutputFile
import com.pdftools.app.domain.pdf.SplitPdfUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SplitModeType {
    EVERY_PAGE,
    BY_RANGES,
    EXTRACT_PAGES
}

sealed interface SplitUiState {
    object Idle : SplitUiState
    object Processing : SplitUiState
    data class Success(val outputFiles: List<SplitOutputFile>) : SplitUiState
    data class Error(val message: String) : SplitUiState
}

class SplitPdfViewModel(
    private val pdfRepository: PdfRepository,
    private val splitPdfUseCase: SplitPdfUseCase = SplitPdfUseCase(pdfRepository)
) : ViewModel() {

    private val _selectedPdfFile = MutableStateFlow<PdfFile?>(null)
    val selectedPdfFile: StateFlow<PdfFile?> = _selectedPdfFile.asStateFlow()

    private val _selectedMode = MutableStateFlow(SplitModeType.EVERY_PAGE)
    val selectedMode: StateFlow<SplitModeType> = _selectedMode.asStateFlow()

    private val _rangeInput = MutableStateFlow("")
    val rangeInput: StateFlow<String> = _rangeInput.asStateFlow()

    private val _extractPagesInput = MutableStateFlow("")
    val extractPagesInput: StateFlow<String> = _extractPagesInput.asStateFlow()

    private val _selectedPagesSet = MutableStateFlow<Set<Int>>(emptySet())
    val selectedPagesSet: StateFlow<Set<Int>> = _selectedPagesSet.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _uiState = MutableStateFlow<SplitUiState>(SplitUiState.Idle)
    val uiState: StateFlow<SplitUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _pageThumbnails = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageThumbnails: StateFlow<Map<Int, Bitmap>> = _pageThumbnails.asStateFlow()

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
            _selectedMode.value = SplitModeType.EVERY_PAGE
            _rangeInput.value = if (pageCount > 1) "1-${minOf(5, pageCount)}" else "1"
            _extractPagesInput.value = "1"
            _selectedPagesSet.value = setOf(0)
            _validationError.value = null
            _uiState.value = SplitUiState.Idle
            _pageThumbnails.value = emptyMap()

            loadThumbnails(context, uri, pageCount)
        }
    }

    fun setSelectedMode(mode: SplitModeType) {
        _selectedMode.value = mode
        validateCurrentInput()
    }

    fun updateRangeInput(input: String) {
        _rangeInput.value = input
        val totalPages = _selectedPdfFile.value?.pageCount ?: 0
        if (totalPages > 0) {
            when (val res = PageRangeParser.parseRanges(input, totalPages)) {
                is PageRangeParseResult.Error -> _validationError.value = res.message
                is PageRangeParseResult.Success -> _validationError.value = null
            }
        }
    }

    fun updateExtractPagesInput(input: String) {
        _extractPagesInput.value = input
        val totalPages = _selectedPdfFile.value?.pageCount ?: 0
        if (totalPages > 0) {
            when (val res = PageRangeParser.parseExtractPages(input, totalPages)) {
                is PageRangeParseResult.Error -> _validationError.value = res.message
                is PageRangeParseResult.Success -> {
                    _validationError.value = null
                    val indices = res.pageGroups.flatten().toSet()
                    _selectedPagesSet.value = indices
                }
            }
        }
    }

    fun togglePageSelection(pageIndex: Int) {
        val currentSet = _selectedPagesSet.value.toMutableSet()
        if (currentSet.contains(pageIndex)) {
            if (currentSet.size > 1) {
                currentSet.remove(pageIndex)
            }
        } else {
            currentSet.add(pageIndex)
        }
        _selectedPagesSet.value = currentSet

        val sorted1Based = currentSet.sorted().map { it + 1 }
        val rangeStr = buildRangeString(sorted1Based)
        _extractPagesInput.value = rangeStr
        _validationError.value = null
    }

    fun clearPdfSelection() {
        _selectedPdfFile.value = null
        _pageThumbnails.value = emptyMap()
        _validationError.value = null
        _uiState.value = SplitUiState.Idle
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorAndKeepFile() {
        _uiState.value = SplitUiState.Idle
    }

    fun splitPdf() {
        val pdfFile = _selectedPdfFile.value ?: return
        val totalPages = pdfFile.pageCount ?: 0
        if (totalPages <= 0) return

        val mode = _selectedMode.value
        val splitMode: SplitMode = when (mode) {
            SplitModeType.EVERY_PAGE -> SplitMode.EveryPage
            SplitModeType.BY_RANGES -> {
                val input = _rangeInput.value
                when (val res = PageRangeParser.parseRanges(input, totalPages)) {
                    is PageRangeParseResult.Error -> {
                        _validationError.value = res.message
                        return
                    }
                    is PageRangeParseResult.Success -> SplitMode.ByRanges(input)
                }
            }
            SplitModeType.EXTRACT_PAGES -> {
                val input = _extractPagesInput.value
                when (val res = PageRangeParser.parseExtractPages(input, totalPages)) {
                    is PageRangeParseResult.Error -> {
                        _validationError.value = res.message
                        return
                    }
                    is PageRangeParseResult.Success -> SplitMode.ExtractPages(input)
                }
            }
        }

        viewModelScope.launch {
            _uiState.value = SplitUiState.Processing
            try {
                val result = splitPdfUseCase(
                    inputUri = pdfFile.uri,
                    baseFileName = pdfFile.displayName,
                    splitMode = splitMode,
                    pageCount = totalPages
                )
                result.fold(
                    onSuccess = { outputs ->
                        _uiState.value = SplitUiState.Success(outputs)
                    },
                    onFailure = { error ->
                        _uiState.value = SplitUiState.Error(
                            error.localizedMessage ?: "Failed to split PDF."
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = SplitUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred."
                )
            }
        }
    }

    fun reset() {
        _uiState.value = SplitUiState.Idle
        _selectedPdfFile.value = null
        _pageThumbnails.value = emptyMap()
        _validationError.value = null
        _userMessage.value = null
    }

    private fun validateCurrentInput() {
        val totalPages = _selectedPdfFile.value?.pageCount ?: 0
        if (totalPages <= 0) return

        when (_selectedMode.value) {
            SplitModeType.EVERY_PAGE -> _validationError.value = null
            SplitModeType.BY_RANGES -> updateRangeInput(_rangeInput.value)
            SplitModeType.EXTRACT_PAGES -> updateExtractPagesInput(_extractPagesInput.value)
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
                        val targetWidth = 160
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

    private fun buildRangeString(sortedPages: List<Int>): String {
        if (sortedPages.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = sortedPages[0]
        var prev = start

        for (i in 1 until sortedPages.size) {
            val curr = sortedPages[i]
            if (curr == prev + 1) {
                prev = curr
            } else {
                if (start == prev) {
                    parts.add("$start")
                } else {
                    parts.add("$start-$prev")
                }
                start = curr
                prev = curr
            }
        }

        if (start == prev) {
            parts.add("$start")
        } else {
            parts.add("$start-$prev")
        }

        return parts.joinToString(", ")
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = PdfRepositoryImpl(context.applicationContext)
            return SplitPdfViewModel(repository) as T
        }
    }
}
