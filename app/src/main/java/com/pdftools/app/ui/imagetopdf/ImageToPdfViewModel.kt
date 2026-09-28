package com.pdftools.app.ui.imagetopdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdftools.app.data.pdf.PdfRepository
import com.pdftools.app.data.pdf.PdfRepositoryImpl
import com.pdftools.app.domain.pdf.ImageSpec
import com.pdftools.app.domain.pdf.ImageToPdfPageSize
import com.pdftools.app.domain.pdf.ImageToPdfQuality
import com.pdftools.app.domain.pdf.ImageToPdfResult
import com.pdftools.app.domain.pdf.ImageToPdfUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImageToPdfUiState {
    data object Idle : ImageToPdfUiState
    data class Processing(val current: Int, val total: Int) : ImageToPdfUiState
    data class Success(val result: ImageToPdfResult) : ImageToPdfUiState
    data class Error(val message: String) : ImageToPdfUiState
}

class ImageToPdfViewModel(
    private val imageToPdfUseCase: ImageToPdfUseCase
) : ViewModel() {

    private val _imagesList = MutableStateFlow<List<ImageToPdfItem>>(emptyList())
    val imagesList: StateFlow<List<ImageToPdfItem>> = _imagesList.asStateFlow()

    private val _pageSize = MutableStateFlow(ImageToPdfPageSize.A4)
    val pageSize: StateFlow<ImageToPdfPageSize> = _pageSize.asStateFlow()

    private val _quality = MutableStateFlow(ImageToPdfQuality.HIGH)
    val quality: StateFlow<ImageToPdfQuality> = _quality.asStateFlow()

    private val _fileName = MutableStateFlow("images")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _uiState = MutableStateFlow<ImageToPdfUiState>(ImageToPdfUiState.Idle)
    val uiState: StateFlow<ImageToPdfUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<String, Bitmap?>>(emptyMap())
    val thumbnails: StateFlow<Map<String, Bitmap?>> = _thumbnails.asStateFlow()

    fun addImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val contentResolver = context.contentResolver
            val newItems = mutableListOf<ImageToPdfItem>()

            for (uri in uris) {
                val displayName = queryDisplayName(contentResolver, uri) ?: "image_${System.currentTimeMillis()}.jpg"
                val exifRotation = queryExifRotation(contentResolver, uri)

                val item = ImageToPdfItem(
                    uri = uri,
                    displayName = displayName,
                    exifRotation = exifRotation
                )
                newItems.add(item)
            }

            val currentList = _imagesList.value.toMutableList()
            val wasEmpty = currentList.isEmpty()
            currentList.addAll(newItems)
            _imagesList.value = currentList

            if (wasEmpty && newItems.isNotEmpty()) {
                val firstName = newItems.first().displayName
                val cleanBaseName = if (firstName.contains('.')) {
                    firstName.substringBeforeLast('.')
                } else firstName
                if (cleanBaseName.isNotBlank()) {
                    _fileName.value = cleanBaseName
                }
            }

            loadThumbnails(context, newItems)
        }
    }

    fun removeImage(id: String) {
        val current = _imagesList.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = current.removeAt(index)
            _imagesList.value = current
            val currentMap = _thumbnails.value.toMutableMap()
            currentMap.remove(item.id)?.recycle()
            _thumbnails.value = currentMap
        }
    }

    fun removeSelectedImages() {
        val current = _imagesList.value.toMutableList()
        val toRemove = current.filter { it.isSelected }
        if (toRemove.isEmpty()) return

        current.removeAll(toRemove)
        _imagesList.value = current

        val currentMap = _thumbnails.value.toMutableMap()
        for (item in toRemove) {
            currentMap.remove(item.id)?.recycle()
        }
        _thumbnails.value = currentMap
    }

    fun toggleImageSelection(id: String) {
        _imagesList.value = _imagesList.value.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun selectAll() {
        _imagesList.value = _imagesList.value.map { it.copy(isSelected = true) }
    }

    fun clearSelection() {
        _imagesList.value = _imagesList.value.map { it.copy(isSelected = false) }
    }

    fun rotateSingleImage(id: String, degrees: Int) {
        _imagesList.value = _imagesList.value.map {
            if (it.id == id) {
                val newRot = (it.userRotation + degrees) % 360
                it.copy(userRotation = if (newRot < 0) newRot + 360 else newRot)
            } else it
        }
    }

    fun rotateSelectedImages(degrees: Int) {
        _imagesList.value = _imagesList.value.map {
            if (it.isSelected) {
                val newRot = (it.userRotation + degrees) % 360
                it.copy(userRotation = if (newRot < 0) newRot + 360 else newRot)
            } else it
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        val current = _imagesList.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _imagesList.value = current
        }
    }

    fun moveSelectedUp() {
        val current = _imagesList.value.toMutableList()
        val selectedIndices = current.indices.filter { current[it].isSelected }
        if (selectedIndices.isEmpty() || selectedIndices.first() == 0) return

        for (idx in selectedIndices) {
            val item = current.removeAt(idx)
            current.add(idx - 1, item)
        }
        _imagesList.value = current
    }

    fun moveSelectedDown() {
        val current = _imagesList.value.toMutableList()
        val selectedIndices = current.indices.filter { current[it].isSelected }
        if (selectedIndices.isEmpty() || selectedIndices.last() == current.size - 1) return

        for (idx in selectedIndices.reversed()) {
            val item = current.removeAt(idx)
            current.add(idx + 1, item)
        }
        _imagesList.value = current
    }

    fun setPageSize(size: ImageToPdfPageSize) {
        _pageSize.value = size
    }

    fun setQuality(q: ImageToPdfQuality) {
        _quality.value = q
    }

    fun setFileName(name: String) {
        _fileName.value = name
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorAndKeepState() {
        _uiState.value = ImageToPdfUiState.Idle
    }

    fun createPdf() {
        val items = _imagesList.value
        if (items.isEmpty()) {
            _userMessage.value = "Please select at least one image to create a PDF."
            return
        }

        if (_uiState.value is ImageToPdfUiState.Processing) return

        viewModelScope.launch {
            _uiState.value = ImageToPdfUiState.Processing(1, items.size)

            val specs = items.map { item ->
                ImageSpec(
                    uri = item.uri,
                    rotationDegrees = item.userRotation,
                    name = item.displayName
                )
            }

            val result = imageToPdfUseCase(
                images = specs,
                baseFileName = _fileName.value,
                pageSize = _pageSize.value,
                quality = _quality.value,
                onProgress = { current, total ->
                    _uiState.value = ImageToPdfUiState.Processing(current, total)
                }
            )

            result.fold(
                onSuccess = { pdfResult ->
                    _uiState.value = ImageToPdfUiState.Success(pdfResult)
                },
                onFailure = { error ->
                    _uiState.value = ImageToPdfUiState.Error(error.localizedMessage ?: "Failed to create PDF from images.")
                }
            )
        }
    }

    fun reset() {
        val currentMap = _thumbnails.value
        for (bmp in currentMap.values) {
            bmp?.recycle()
        }
        _thumbnails.value = emptyMap()
        _imagesList.value = emptyList()
        _pageSize.value = ImageToPdfPageSize.A4
        _quality.value = ImageToPdfQuality.HIGH
        _fileName.value = "images"
        _uiState.value = ImageToPdfUiState.Idle
        _userMessage.value = null
    }

    private fun loadThumbnails(context: Context, items: List<ImageToPdfItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val newThumbnails = mutableMapOf<String, Bitmap?>()

            for (item in items) {
                val bitmap = try {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(item.uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, boundsOptions)
                    }
                    val origW = boundsOptions.outWidth
                    val origH = boundsOptions.outHeight

                    if (origW > 0 && origH > 0) {
                        var sampleSize = 1
                        val target = 360
                        val maxDim = maxOf(origW, origH)
                        if (maxDim > target) {
                            val half = maxDim / 2
                            while ((half / sampleSize) >= target) {
                                sampleSize *= 2
                            }
                        }
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        contentResolver.openInputStream(item.uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, decodeOptions)
                        }
                    } else null
                } catch (_: Exception) {
                    null
                }
                newThumbnails[item.id] = bitmap
            }

            withContext(Dispatchers.Main) {
                val currentMap = _thumbnails.value.toMutableMap()
                currentMap.putAll(newThumbnails)
                _thumbnails.value = currentMap
            }
        }
    }

    private suspend fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx != -1 && !cursor.isNull(nameIdx)) {
                    return@withContext cursor.getString(nameIdx)
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    private suspend fun queryExifRotation(contentResolver: ContentResolver, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository: PdfRepository = PdfRepositoryImpl(context.applicationContext)
            val useCase = ImageToPdfUseCase(repository)
            return ImageToPdfViewModel(useCase) as T
        }
    }
}
