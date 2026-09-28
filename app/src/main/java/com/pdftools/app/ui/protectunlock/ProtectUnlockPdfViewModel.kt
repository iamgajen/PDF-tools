package com.pdftools.app.ui.protectunlock

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdftools.app.data.pdf.PdfRepository
import com.pdftools.app.data.pdf.PdfRepositoryImpl
import com.pdftools.app.domain.pdf.PdfFile
import com.pdftools.app.domain.pdf.ProtectPdfParams
import com.pdftools.app.domain.pdf.ProtectUnlockOutputFile
import com.pdftools.app.domain.pdf.ProtectUnlockPdfUseCase
import com.pdftools.app.domain.pdf.UnlockPdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProtectUnlockUiState {
    object Idle : ProtectUnlockUiState
    object Loading : ProtectUnlockUiState
    data class Processing(val message: String) : ProtectUnlockUiState
    data class Success(val outputFile: ProtectUnlockOutputFile) : ProtectUnlockUiState
    data class Error(val message: String) : ProtectUnlockUiState
}

class ProtectUnlockPdfViewModel(
    private val pdfRepository: PdfRepository,
    private val protectUnlockPdfUseCase: ProtectUnlockPdfUseCase = ProtectUnlockPdfUseCase(pdfRepository)
) : ViewModel() {

    private val _selectedPdfFile = MutableStateFlow<PdfFile?>(null)
    val selectedPdfFile: StateFlow<PdfFile?> = _selectedPdfFile.asStateFlow()

    private val _isPdfEncrypted = MutableStateFlow(false)
    val isPdfEncrypted: StateFlow<Boolean> = _isPdfEncrypted.asStateFlow()

    private val _passwordInput = MutableStateFlow("")
    val passwordInput: StateFlow<String> = _passwordInput.asStateFlow()

    private val _confirmPasswordInput = MutableStateFlow("")
    val confirmPasswordInput: StateFlow<String> = _confirmPasswordInput.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val isPasswordVisible: StateFlow<Boolean> = _isPasswordVisible.asStateFlow()

    private val _isConfirmPasswordVisible = MutableStateFlow(false)
    val isConfirmPasswordVisible: StateFlow<Boolean> = _isConfirmPasswordVisible.asStateFlow()

    private val _preventPrinting = MutableStateFlow(false)
    val preventPrinting: StateFlow<Boolean> = _preventPrinting.asStateFlow()

    private val _preventCopying = MutableStateFlow(false)
    val preventCopying: StateFlow<Boolean> = _preventCopying.asStateFlow()

    private val _preventModifying = MutableStateFlow(false)
    val preventModifying: StateFlow<Boolean> = _preventModifying.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _uiState = MutableStateFlow<ProtectUnlockUiState>(ProtectUnlockUiState.Idle)
    val uiState: StateFlow<ProtectUnlockUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun setPdfUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ProtectUnlockUiState.Loading
            clearInputs()

            val fileInfo = pdfRepository.getPdfFileInfo(uri)
            if (fileInfo.isEncrypted) {
                _selectedPdfFile.value = fileInfo
                _isPdfEncrypted.value = true
                _uiState.value = ProtectUnlockUiState.Idle
            } else if (fileInfo.isValid) {
                _selectedPdfFile.value = fileInfo
                _isPdfEncrypted.value = false
                _uiState.value = ProtectUnlockUiState.Idle
            } else {
                _userMessage.value = fileInfo.errorMessage ?: "Unable to read this PDF file."
                _selectedPdfFile.value = null
                _isPdfEncrypted.value = false
                _uiState.value = ProtectUnlockUiState.Idle
            }
        }
    }

    fun setPasswordInput(input: String) {
        _passwordInput.value = input
        _validationError.value = null
    }

    fun setConfirmPasswordInput(input: String) {
        _confirmPasswordInput.value = input
        _validationError.value = null
    }

    fun togglePasswordVisibility() {
        _isPasswordVisible.value = !_isPasswordVisible.value
    }

    fun toggleConfirmPasswordVisibility() {
        _isConfirmPasswordVisible.value = !_isConfirmPasswordVisible.value
    }

    fun setPreventPrinting(prevent: Boolean) {
        _preventPrinting.value = prevent
    }

    fun setPreventCopying(prevent: Boolean) {
        _preventCopying.value = prevent
    }

    fun setPreventModifying(prevent: Boolean) {
        _preventModifying.value = prevent
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearErrorAndKeepState() {
        _uiState.value = ProtectUnlockUiState.Idle
    }

    fun reset() {
        clearInputs()
        _selectedPdfFile.value = null
        _isPdfEncrypted.value = false
        _uiState.value = ProtectUnlockUiState.Idle
        _userMessage.value = null
    }

    fun processAction() {
        val file = _selectedPdfFile.value ?: run {
            _userMessage.value = "No PDF file selected."
            return
        }

        val pass = _passwordInput.value
        if (pass.isEmpty()) {
            _validationError.value = "Password cannot be empty."
            return
        }

        if (_isPdfEncrypted.value) {
            // Unlock Flow
            _validationError.value = null
            _uiState.value = ProtectUnlockUiState.Processing("Unlocking PDF...")

            viewModelScope.launch(Dispatchers.IO) {
                val params = UnlockPdfParams(
                    inputUri = file.uri,
                    baseFileName = file.displayName,
                    password = pass
                )

                val result = protectUnlockPdfUseCase.unlockPdf(params)
                if (result.isSuccess) {
                    _uiState.value = ProtectUnlockUiState.Success(result.getOrThrow())
                    clearInputs()
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to unlock PDF."
                    _uiState.value = ProtectUnlockUiState.Error(err)
                }
            }
        } else {
            // Protect Flow
            val confirm = _confirmPasswordInput.value
            if (pass != confirm) {
                _validationError.value = "Passwords do not match."
                return
            }

            _validationError.value = null
            _uiState.value = ProtectUnlockUiState.Processing("Protecting PDF...")

            viewModelScope.launch(Dispatchers.IO) {
                val params = ProtectPdfParams(
                    inputUri = file.uri,
                    baseFileName = file.displayName,
                    password = pass,
                    preventPrinting = _preventPrinting.value,
                    preventCopying = _preventCopying.value,
                    preventModifying = _preventModifying.value
                )

                val result = protectUnlockPdfUseCase.protectPdf(params)
                if (result.isSuccess) {
                    _uiState.value = ProtectUnlockUiState.Success(result.getOrThrow())
                    clearInputs()
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to protect PDF."
                    _uiState.value = ProtectUnlockUiState.Error(err)
                }
            }
        }
    }

    private fun clearInputs() {
        _passwordInput.value = ""
        _confirmPasswordInput.value = ""
        _isPasswordVisible.value = false
        _isConfirmPasswordVisible.value = false
        _preventPrinting.value = false
        _preventCopying.value = false
        _preventModifying.value = false
        _validationError.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = PdfRepositoryImpl(context.applicationContext)
            val useCase = ProtectUnlockPdfUseCase(repo)
            return ProtectUnlockPdfViewModel(repo, useCase) as T
        }
    }
}
