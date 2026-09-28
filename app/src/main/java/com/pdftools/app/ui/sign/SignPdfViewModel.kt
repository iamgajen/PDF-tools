package com.pdftools.app.ui.sign

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
import com.pdftools.app.domain.pdf.PdfFile
import com.pdftools.app.domain.pdf.SignOutputFile
import com.pdftools.app.domain.pdf.SignPdfParams
import com.pdftools.app.domain.pdf.SignPdfUseCase
import com.pdftools.app.domain.pdf.SignatureSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class PlacedSignature(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val normalizedX: Float = 0.25f,
    val normalizedY: Float = 0.35f,
    val normalizedWidth: Float = 0.5f,
    val normalizedHeight: Float = 0.2f,
    val rotationDegrees: Float = 0f,
    val bitmap: Bitmap
)

sealed interface SignPdfUiState {
    object Idle : SignPdfUiState
    object Loading : SignPdfUiState
    object Saving : SignPdfUiState
    data class Success(val outputFile: SignOutputFile) : SignPdfUiState
    data class Error(val message: String) : SignPdfUiState
}

class SignPdfViewModel(
    private val pdfRepository: PdfRepository,
    private val signPdfUseCase: SignPdfUseCase = SignPdfUseCase(pdfRepository)
) : ViewModel() {

    private val _selectedPdfFile = MutableStateFlow<PdfFile?>(null)
    val selectedPdfFile: StateFlow<PdfFile?> = _selectedPdfFile.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _signatures = MutableStateFlow<List<PlacedSignature>>(emptyList())
    val signatures: StateFlow<List<PlacedSignature>> = _signatures.asStateFlow()

    private val _selectedSignatureId = MutableStateFlow<String?>(null)
    val selectedSignatureId: StateFlow<String?> = _selectedSignatureId.asStateFlow()

    private val _undoStack = MutableStateFlow<List<List<PlacedSignature>>>(emptyList())

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _pageThumbnails = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageThumbnails: StateFlow<Map<Int, Bitmap>> = _pageThumbnails.asStateFlow()

    private val _uiState = MutableStateFlow<SignPdfUiState>(SignPdfUiState.Idle)
    val uiState: StateFlow<SignPdfUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun setPdfUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SignPdfUiState.Loading
            val fileInfo = pdfRepository.getPdfFileInfo(uri)
            if (!fileInfo.isValid) {
                _userMessage.value = fileInfo.errorMessage ?: "Unable to read this PDF file."
                _selectedPdfFile.value = null
                _uiState.value = SignPdfUiState.Idle
                return@launch
            }

            val pageCount = fileInfo.pageCount ?: 0
            if (pageCount <= 0) {
                _userMessage.value = "Selected PDF has no readable pages."
                _selectedPdfFile.value = null
                _uiState.value = SignPdfUiState.Idle
                return@launch
            }

            _selectedPdfFile.value = fileInfo
            _currentPageIndex.value = 0
            _signatures.value = emptyList()
            _selectedSignatureId.value = null
            _undoStack.value = emptyList()
            _canUndo.value = false
            _uiState.value = SignPdfUiState.Idle
            _pageThumbnails.value = emptyMap()

            loadThumbnails(context, uri, pageCount)
        }
    }

    fun setCurrentPageIndex(index: Int) {
        val totalPages = _selectedPdfFile.value?.pageCount ?: 0
        if (index in 0 until totalPages) {
            _currentPageIndex.value = index
            _selectedSignatureId.value = null
        }
    }

    fun selectSignature(id: String?) {
        _selectedSignatureId.value = id
    }

    private fun pushUndoState() {
        val currentStack = _undoStack.value.toMutableList()
        currentStack.add(_signatures.value)
        _undoStack.value = currentStack
        _canUndo.value = true
    }

    fun undo() {
        val currentStack = _undoStack.value.toMutableList()
        if (currentStack.isNotEmpty()) {
            val previousState = currentStack.removeAt(currentStack.size - 1)
            _signatures.value = previousState
            _undoStack.value = currentStack
            _canUndo.value = currentStack.isNotEmpty()
            _selectedSignatureId.value = null
        }
    }

    fun addSignature(bitmap: Bitmap) {
        val pageIdx = _currentPageIndex.value
        val pageBitmap = _pageThumbnails.value[pageIdx]
        
        val pageAspect = if (pageBitmap != null && pageBitmap.height > 0) {
            pageBitmap.width.toFloat() / pageBitmap.height.toFloat()
        } else 0.75f

        val bmpAspect = if (bitmap.height > 0) {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        } else 2.0f

        val normWidth = 0.45f
        // normHeight in page space maintaining bitmap aspect ratio:
        val normHeight = (normWidth / bmpAspect) * pageAspect

        pushUndoState()
        val newSig = PlacedSignature(
            pageIndex = pageIdx,
            normalizedX = (1.0f - normWidth) / 2.0f,
            normalizedY = (1.0f - normHeight) / 2.0f,
            normalizedWidth = normWidth,
            normalizedHeight = normHeight,
            bitmap = bitmap
        )

        _signatures.value = _signatures.value + newSig
        _selectedSignatureId.value = newSig.id
    }

    fun updateSignaturePosition(id: String, nx: Float, ny: Float) {
        _signatures.value = _signatures.value.map { sig ->
            if (sig.id == id) {
                sig.copy(
                    normalizedX = nx.coerceIn(0f, 1f - sig.normalizedWidth),
                    normalizedY = ny.coerceIn(0f, 1f - sig.normalizedHeight)
                )
            } else sig
        }
    }

    fun updateSignatureSize(id: String, nw: Float) {
        _signatures.value = _signatures.value.map { sig ->
            if (sig.id == id) {
                val pageIdx = sig.pageIndex
                val pageBitmap = _pageThumbnails.value[pageIdx]
                val pageAspect = if (pageBitmap != null && pageBitmap.height > 0) {
                    pageBitmap.width.toFloat() / pageBitmap.height.toFloat()
                } else 0.75f

                val bmpAspect = if (sig.bitmap.height > 0) {
                    sig.bitmap.width.toFloat() / sig.bitmap.height.toFloat()
                } else 2.0f

                val clampedWidth = nw.coerceIn(0.1f, 0.95f)
                val newHeight = (clampedWidth / bmpAspect) * pageAspect

                sig.copy(
                    normalizedWidth = clampedWidth,
                    normalizedHeight = newHeight.coerceIn(0.05f, 0.95f)
                )
            } else sig
        }
    }

    fun rotateSignature(id: String, deltaDegrees: Float) {
        pushUndoState()
        _signatures.value = _signatures.value.map { sig ->
            if (sig.id == id) {
                val newRot = (sig.rotationDegrees + deltaDegrees) % 360f
                sig.copy(rotationDegrees = if (newRot < 0) newRot + 360f else newRot)
            } else sig
        }
    }

    fun deleteSignature(id: String) {
        pushUndoState()
        _signatures.value = _signatures.value.filter { it.id != id }
        if (_selectedSignatureId.value == id) {
            _selectedSignatureId.value = null
        }
    }

    fun deleteSelectedSignature() {
        _selectedSignatureId.value?.let { id ->
            deleteSignature(id)
        }
    }

    fun signPdf() {
        val file = _selectedPdfFile.value ?: return
        val currentSigs = _signatures.value
        if (currentSigs.isEmpty()) {
            _userMessage.value = "Please add at least one signature to sign the PDF."
            return
        }

        val specs = currentSigs.map { sig ->
            SignatureSpec(
                id = sig.id,
                pageIndex = sig.pageIndex,
                normalizedX = sig.normalizedX,
                normalizedY = sig.normalizedY,
                normalizedWidth = sig.normalizedWidth,
                normalizedHeight = sig.normalizedHeight,
                rotationDegrees = sig.rotationDegrees,
                bitmap = sig.bitmap
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SignPdfUiState.Saving
            val params = SignPdfParams(
                inputUri = file.uri,
                baseFileName = file.displayName,
                signatures = specs
            )

            val result = signPdfUseCase(params)
            if (result.isSuccess) {
                _uiState.value = SignPdfUiState.Success(result.getOrThrow())
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to sign PDF."
                _uiState.value = SignPdfUiState.Error(errorMsg)
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorAndKeepState() {
        _uiState.value = SignPdfUiState.Idle
    }

    fun reset() {
        _uiState.value = SignPdfUiState.Idle
        _selectedPdfFile.value = null
        _currentPageIndex.value = 0
        _signatures.value = emptyList()
        _selectedSignatureId.value = null
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
                        val targetWidth = 600
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
            return SignPdfViewModel(repository) as T
        }
    }
}
