package com.pdftools.app.domain.pdf

import android.net.TestUri
import android.net.Uri
import com.pdftools.app.data.pdf.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream

class ProtectUnlockPdfUseCaseTest {

    private lateinit var fakeRepository: FakePdfRepository
    private lateinit var useCase: ProtectUnlockPdfUseCase
    private val testUri = TestUri("content://test/document.pdf")

    @Before
    fun setUp() {
        fakeRepository = FakePdfRepository()
        useCase = ProtectUnlockPdfUseCase(fakeRepository, Dispatchers.Unconfined)
    }

    @Test
    fun testProtectNormalPdfSuccess() = runBlocking {
        val params = ProtectPdfParams(
            inputUri = testUri,
            baseFileName = "document.pdf",
            password = "secretPassword123"
        )

        val result = useCase.protectPdf(params)
        assertTrue("Expected protect to succeed", result.isSuccess)
        val res = result.getOrNull()!!
        assertEquals("document_protected.pdf", res.displayName)
        assertFalse("Output of protect should not be unlocked", res.isUnlocked)
    }

    @Test
    fun testProtectEmptyPasswordRejected() = runBlocking {
        val params = ProtectPdfParams(
            inputUri = testUri,
            baseFileName = "document.pdf",
            password = ""
        )

        val result = useCase.protectPdf(params)
        assertTrue("Expected failure for empty password", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Password cannot be empty") == true)
    }

    @Test
    fun testProtectPermissionRestrictions() = runBlocking {
        val params = ProtectPdfParams(
            inputUri = testUri,
            baseFileName = "document.pdf",
            password = "secretPassword123",
            preventPrinting = true,
            preventCopying = true,
            preventModifying = true
        )

        val result = useCase.protectPdf(params)
        assertTrue(result.isSuccess)
        val last = fakeRepository.lastProtectParams
        assertNotNull(last)
        assertTrue(last!!.preventPrinting)
        assertTrue(last.preventCopying)
        assertTrue(last.preventModifying)
    }

    @Test
    fun testUnlockPdfSuccess() = runBlocking {
        val params = UnlockPdfParams(
            inputUri = testUri,
            baseFileName = "document_protected.pdf",
            password = "correctPassword"
        )

        val result = useCase.unlockPdf(params)
        assertTrue("Expected unlock to succeed", result.isSuccess)
        val res = result.getOrNull()!!
        assertEquals("document_unlocked.pdf", res.displayName)
        assertTrue("Output of unlock should be unlocked", res.isUnlocked)
    }

    @Test
    fun testUnlockEmptyPasswordRejected() = runBlocking {
        val params = UnlockPdfParams(
            inputUri = testUri,
            baseFileName = "document_protected.pdf",
            password = ""
        )

        val result = useCase.unlockPdf(params)
        assertTrue("Expected failure for empty password on unlock", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Password cannot be empty") == true)
    }

    @Test
    fun testUnlockIncorrectPasswordRejected() = runBlocking {
        fakeRepository.shouldFailUnlock = true
        fakeRepository.unlockException = IllegalArgumentException("Incorrect password.")

        val params = UnlockPdfParams(
            inputUri = testUri,
            baseFileName = "document_protected.pdf",
            password = "wrongPassword"
        )

        val result = useCase.unlockPdf(params)
        assertTrue("Expected failure for incorrect password", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Incorrect password") == true)
    }

    @Test
    fun testOriginalFileRemainsUnchanged() = runBlocking {
        val originalFile = File.createTempFile("original_", ".pdf")
        originalFile.writeText("Original PDF contents")
        val originalSize = originalFile.length()

        val params = ProtectPdfParams(
            inputUri = testUri,
            baseFileName = "document.pdf",
            password = "password123"
        )

        val result = useCase.protectPdf(params)
        assertTrue(result.isSuccess)
        assertEquals(originalSize, originalFile.length())
        originalFile.delete()
        Unit
    }

    @Test
    fun testStorageFailureHandled() = runBlocking {
        fakeRepository.shouldFailProtect = true
        fakeRepository.protectException = IOException("Storage is full.")

        val params = ProtectPdfParams(
            inputUri = testUri,
            baseFileName = "document.pdf",
            password = "password123"
        )

        val result = useCase.protectPdf(params)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Storage is full") == true)
    }

    private class FakePdfRepository : PdfRepository {
        var shouldFailProtect = false
        var protectException: Exception = IOException("Error")

        var shouldFailUnlock = false
        var unlockException: Exception = IllegalArgumentException("Incorrect password.")

        var lastProtectParams: ProtectPdfParams? = null
        var lastUnlockParams: UnlockPdfParams? = null

        override suspend fun getPdfFileInfo(uri: Uri): PdfFile {
            return PdfFile(
                uri = uri,
                displayName = "test.pdf",
                size = 100000L,
                pageCount = 5,
                isValid = true
            )
        }

        override suspend fun protectPdf(params: ProtectPdfParams): Result<ProtectUnlockOutputFile> {
            lastProtectParams = params
            if (shouldFailProtect) {
                return Result.failure(protectException)
            }

            val rawBaseName = params.baseFileName
            val cleanName = if (rawBaseName.endsWith(".pdf", ignoreCase = true)) {
                rawBaseName.substring(0, rawBaseName.length - 4)
            } else {
                rawBaseName
            }

            val targetName = "${cleanName}_protected.pdf"
            val file = File.createTempFile("fake_protected_", ".pdf")

            return Result.success(
                ProtectUnlockOutputFile(
                    file = file,
                    uri = TestUri("content://test/$targetName"),
                    displayName = targetName,
                    size = 50000L,
                    pageCount = 5,
                    isUnlocked = false
                )
            )
        }

        override suspend fun unlockPdf(params: UnlockPdfParams): Result<ProtectUnlockOutputFile> {
            lastUnlockParams = params
            if (shouldFailUnlock) {
                return Result.failure(unlockException)
            }

            val rawBaseName = params.baseFileName
            val cleanName = if (rawBaseName.endsWith(".pdf", ignoreCase = true)) {
                val base = rawBaseName.substring(0, rawBaseName.length - 4)
                if (base.endsWith("_protected", ignoreCase = true)) {
                    base.substring(0, base.length - 10)
                } else {
                    base
                }
            } else {
                rawBaseName
            }

            val targetName = "${cleanName}_unlocked.pdf"
            val file = File.createTempFile("fake_unlocked_", ".pdf")

            return Result.success(
                ProtectUnlockOutputFile(
                    file = file,
                    uri = TestUri("content://test/$targetName"),
                    displayName = targetName,
                    size = 48000L,
                    pageCount = 5,
                    isUnlocked = true
                )
            )
        }

        override suspend fun mergePdfs(inputUris: List<Uri>, outputStream: OutputStream): Result<Int> {
            return Result.success(1)
        }

        override suspend fun createTempMergedFile(inputUris: List<Uri>): Result<File> {
            return Result.success(File.createTempFile("fake_", ".pdf"))
        }

        override suspend fun splitPdf(
            inputUri: Uri,
            baseFileName: String,
            splitMode: SplitMode,
            pageGroups: List<List<Int>>
        ): Result<List<SplitOutputFile>> {
            return Result.success(emptyList())
        }

        override suspend fun organizePdf(
            inputUri: Uri,
            baseFileName: String,
            pageSpecs: List<OrganizePageSpec>
        ): Result<OrganizeOutputFile> {
            return Result.failure(NotImplementedError())
        }

        override suspend fun compressPdf(
            inputUri: Uri,
            baseFileName: String,
            level: CompressionLevel
        ): Result<CompressPdfResult> {
            return Result.failure(NotImplementedError())
        }
    }
}
