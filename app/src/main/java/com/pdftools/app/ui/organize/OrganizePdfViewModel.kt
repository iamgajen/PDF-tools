package com.pdftools.app.ui.organize

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
import com.pdftools.app.domain.pdf.OrganizeOutputFile
import com.pdftools.app.domain.pdf.OrganizePageSpec
import com.pdftools.app.domain.pdf.OrganizePdfUseCase
import com.pdftools.app.domain.pdf.PdfFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class OrganizePageItem(
    val id: String = UUID.randomUUID().toString(),
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0,
    val isSelected: Boolean = false
)

sealed interface OrganizeUiState {
    object Idle : OrganizeUiState
    object Processing : OrganizeUiState
    data class Success(val outputFile: OrganizeOutputFile) : OrganizeUiState
    data class Error(val message: String) : OrganizeUiState
}

class OrganizePdfViewModel(
    private val pdfRepository: PdfRepository,
    private val organizePdfUseCase: OrganizePdfUseCase = OrganizePdfUseCase(pdfRepository)
) : ViewModel() {

    private val _selectedPdfFile = MutableStateFlow<PdfFile?>(null)
    val selectedPdfFile: StateFlow<PdfFile?> = _selectedPdfFile.asStateFlow()

    private val _pagesList = MutableStateFlow<List<OrganizePageItem>>(emptyList())
    val pagesList: StateFlow<List<OrganizePageItem>> = _pagesList.asStateFlow()

    private val _undoStack = MutableStateFlow<List<List<OrganizePageItem>>>(emptyList())

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _uiState = MutableStateFlow<OrganizeUiState>(OrganizeUiState.Idle)
    val uiState: StateFlow<OrganizeUiState> = _uiState.asStateFlow()

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
            _pagesList.value = (0 until pageCount).map { idx ->
                OrganizePageItem(originalPageIndex = idx)
            }
            _undoStack.value = emptyList()
            _canUndo.value = false
            _uiState.value = OrganizeUiState.Idle
            _pageThumbnails.value = emptyMap()

            loadThumbnails(context, uri, pageCount)
        }
    }

    private fun pushUndoState() {
        val currentStack = _undoStack.value.toMutableList()
        currentStack.add(_pagesList.value)
        _undoStack.value = currentStack
        _canUndo.value = true
    }

    fun undo() {
        val currentStack = _undoStack.value.toMutableList()
        if (currentStack.isNotEmpty()) {
            val previousState = currentStack.removeAt(currentStack.size - 1)
            _pagesList.value = previousState
            _undoStack.value = currentStack
            _canUndo.value = currentStack.isNotEmpty()
        }
    }

    fun togglePageSelection(id: String) {
        _pagesList.value = _pagesList.value.map { item ->
            if (item.id == id) item.copy(isSelected = !item.isSelected) else item
        }
    }

    fun selectAll() {
        _pagesList.value = _pagesList.value.map { item ->
            item.copy(isSelected = true)
        }
    }

    fun clearSelection() {
        _pagesList.value = _pagesList.value.map { item ->
            item.copy(isSelected = false)
        }
    }

    fun rotateSelectedPages(degreesDelta: Int) {
        val hasSelection = _pagesList.value.any { it.isSelected }
        if (!hasSelection) {
            _userMessage.value = "Please select at least one page to rotate."
            return
        }

        pushUndoState()
        _pagesList.value = _pagesList.value.map { item ->
            if (item.isSelected) {
                val newRot = (item.rotationDegrees + degreesDelta) % 360
                val normalizedRot = if (newRot < 0) newRot + 360 else newRot
                item.copy(rotationDegrees = normalizedRot)
            } else item
        }
    }

    fun rotateSinglePage(id: String, degreesDelta: Int) {
        pushUndoState()
        _pagesList.value = _pagesList.value.map { item ->
            if (item.id == id) {
                val newRot = (item.rotationDegrees + degreesDelta) % 360
                val normalizedRot = if (newRot < 0) newRot + 360 else newRot
                item.copy(rotationDegrees = normalizedRot)
            } else item
        }
    }

    fun deleteSelectedPages() {
        val selectedCount = _pagesList.value.count { it.isSelected }
        if (selectedCount == 0) {
            _userMessage.value = "Please select at least one page to delete."
            return
        }

        if (selectedCount >= _pagesList.value.size) {
            _userMessage.value = "Cannot delete all pages. The document must contain at least one page."
            return
        }

        pushUndoState()
        _pagesList.value = _pagesList.value.filter { !it.isSelected }
    }

    fun deleteSinglePage(id: String) {
        if (_pagesList.value.size <= 1) {
            _userMessage.value = "Cannot delete the last remaining page."
            return
        }

        pushUndoState()
        _pagesList.value = _pagesList.value.filter { it.id != id }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val current = _pagesList.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            pushUndoState()
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _pagesList.value = current
        }
    }

    fun moveSelectedUp() {
        val current = _pagesList.value.toMutableList()
        val selectedIndices = current.indices.filter { current[it].isSelected }
        if (selectedIndices.isEmpty() || selectedIndices.first() == 0) return

        pushUndoState()
        for (idx in selectedIndices) {
            val item = current.removeAt(idx)
            current.add(idx - 1, item)
        }
        _pagesList.value = current
    }

    fun moveSelectedDown() {
        val current = _pagesList.value.toMutableList()
        val selectedIndices = current.indices.filter { current[it].isSelected }.reversed()
        if (selectedIndices.isEmpty() || selectedIndices.first() == current.size - 1) return

        pushUndoState()
        for (idx in selectedIndices) {
            val item = current.removeAt(idx)
            current.add(idx + 1, item)
        }
        _pagesList.value = current
    }

    fun organizePdf() {
        val pdfFile = _selectedPdfFile.value ?: return
        val currentPages = _pagesList.value
        if (currentPages.isEmpty()) {
            _userMessage.value = "Cannot create an empty PDF. Please keep at least one page."
            return
        }

        val specs = currentPages.map { item ->
            OrganizePageSpec(
                originalPageIndex = item.originalPageIndex,
                rotationDegrees = item.rotationDegrees
            )
        }

        viewModelScope.launch {
            _uiState.value = OrganizeUiState.Processing
            try {
                val result = organizePdfUseCase(
                    inputUri = pdfFile.uri,
                    baseFileName = pdfFile.displayName,
                    pageSpecs = specs
                )
                result.fold(
                    onSuccess = { output ->
                        _uiState.value = OrganizeUiState.Success(output)
                    },
                    onFailure = { error ->
                        _uiState.value = OrganizeUiState.Error(
                            error.localizedMessage ?: "Failed to organize PDF."
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = OrganizeUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred."
                )
            }
        }
    }

    fun clearPdfSelection() {
        _selectedPdfFile.value = null
        _pagesList.value = emptyList()
        _undoStack.value = emptyList()
        _canUndo.value = false
        _pageThumbnails.value = emptyMap()
        _uiState.value = OrganizeUiState.Idle
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorAndKeepState() {
        _uiState.value = OrganizeUiState.Idle
    }

    fun reset() {
        _uiState.value = OrganizeUiState.Idle
        _selectedPdfFile.value = null
        _pagesList.value = emptyList()
        _undoStack.value = emptyList()
        _canUndo.value = false
        _pageThumbnails.value = emptyMap()
        _userMessage.value = null
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
            val repository = PdfRepositoryImpl(context.applicationContext)
            return OrganizePdfViewModel(repository) as T
        }
    }
}
