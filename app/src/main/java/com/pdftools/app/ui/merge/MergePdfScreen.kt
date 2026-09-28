package com.pdftools.app.ui.merge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdftools.app.domain.pdf.PdfFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MergePdfViewModel = viewModel(factory = MergePdfViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val pdfFiles by viewModel.pdfFiles.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val selectPdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addUris(uris)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            val fileName = queryFileName(context, destinationUri) ?: "merged.pdf"
            viewModel.mergePdfsToStream(
                context = context,
                outputUri = destinationUri,
                outputFileName = fileName,
                outputStreamProvider = { context.contentResolver.openOutputStream(destinationUri) }
            )
        }
    }

    // Auto-launch picker on first launch if list is empty
    var hasLaunchedPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasLaunchedPicker && pdfFiles.isEmpty()) {
            hasLaunchedPicker = true
            selectPdfsLauncher.launch(arrayOf("application/pdf"))
        }
    }

    val validFilesCount = pdfFiles.count { it.isValid }

    if (uiState is MergeUiState.Success) {
        val successState = uiState as MergeUiState.Success
        MergePdfResultScreen(
            outputUri = successState.outputUri,
            fileName = successState.fileName,
            fileSize = successState.fileSize,
            pageCount = successState.pageCount,
            onDone = {
                viewModel.reset()
                onBack()
            },
            modifier = modifier
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (pdfFiles.isEmpty()) {
                EmptyPdfSelectionContent(
                    onSelectFiles = { selectPdfsLauncher.launch(arrayOf("application/pdf")) }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(
                            items = pdfFiles,
                            key = { _, item -> item.id }
                        ) { index, pdfFile ->
                            PdfFileItemCard(
                                pdfFile = pdfFile,
                                isFirst = index == 0,
                                isLast = index == pdfFiles.size - 1,
                                onMoveUp = { viewModel.moveUp(index) },
                                onMoveDown = { viewModel.moveDown(index) },
                                onRemove = { viewModel.removeFile(pdfFile.id) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { selectPdfsLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add More PDFs", fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            if (pdfFiles.any { !it.isValid }) {
                                Text(
                                    text = "Some files cannot be read and will be excluded.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    createDocumentLauncher.launch("merged.pdf")
                                },
                                enabled = validFilesCount >= 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Default.Merge, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (validFilesCount < 2) "Select at least 2 PDFs" else "Merge $validFilesCount PDFs",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Processing Overlay
            if (uiState is MergeUiState.Processing) {
                Dialog(
                    onDismissRequest = { },
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                ) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Merging PDFs...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please wait, processing locally on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error Dialog
            if (uiState is MergeUiState.Error) {
                val errorMsg = (uiState as MergeUiState.Error).message
                AlertDialog(
                    onDismissRequest = { viewModel.clearErrorAndKeepFiles() },
                    title = { Text("Merge Failed", fontWeight = FontWeight.Bold) },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearErrorAndKeepFiles() }) {
                            Text("OK")
                        }
                    }
                )
            }

            // User Message Snackbar/Dialog
            if (userMessage != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.clearUserMessage() },
                    title = { Text("Notice") },
                    text = { Text(userMessage ?: "") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearUserMessage() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyPdfSelectionContent(
    onSelectFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No PDF files selected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select two or more PDF files from your device to merge them into a single document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSelectFiles,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select PDF Files", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PdfFileItemCard(
    pdfFile: PdfFile,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (pdfFile.isValid) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = if (pdfFile.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdfFile.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (pdfFile.isValid) {
                    Row {
                        Text(
                            text = pdfFile.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pdfFile.pageCount != null) {
                            Text(
                                text = " • ${pdfFile.pageCount} ${if (pdfFile.pageCount == 1) "page" else "pages"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = pdfFile.errorMessage ?: "Invalid PDF file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move Up",
                        tint = if (!isFirst) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move Down",
                        tint = if (!isLast) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray.copy(alpha = 0.3f)
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1 && !cursor.isNull(nameIndex)) {
                name = cursor.getString(nameIndex)
            }
        }
    } catch (_: Exception) {
    }
    return name
}
