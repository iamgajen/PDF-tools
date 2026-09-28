package com.pdftools.app.ui.compress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdftools.app.domain.pdf.CompressionLevel
import com.pdftools.app.domain.pdf.PdfFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompressPdfViewModel = viewModel(factory = CompressPdfViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val pdfFile by viewModel.selectedPdfFile.collectAsState()
    val selectedLevel by viewModel.selectedLevel.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val selectPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setPdfUri(context, uri)
        }
    }

    var hasLaunchedPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasLaunchedPicker && pdfFile == null) {
            hasLaunchedPicker = true
            selectPdfLauncher.launch(arrayOf("application/pdf"))
        }
    }

    if (uiState is CompressUiState.Success) {
        val successState = uiState as CompressUiState.Success
        CompressPdfResultScreen(
            result = successState.result,
            onDone = {
                viewModel.reset()
                onBack()
            },
            modifier = modifier
        )
        return
    }

    if (uiState is CompressUiState.AlreadyOptimized) {
        val optimizedState = uiState as CompressUiState.AlreadyOptimized
        CompressPdfResultScreen(
            result = optimizedState.result,
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
                title = { Text("Compress PDF", fontWeight = FontWeight.Bold) },
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
            val currentPdf = pdfFile
            if (currentPdf == null) {
                EmptyCompressPdfSelectionContent(
                    onSelectFile = { selectPdfLauncher.launch(arrayOf("application/pdf")) }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    SelectedPdfHeaderCard(
                        pdfFile = currentPdf,
                        onChangeFile = { selectPdfLauncher.launch(arrayOf("application/pdf")) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Compression Level",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CompressionLevelCard(
                        level = CompressionLevel.HIGH_QUALITY,
                        isSelected = selectedLevel == CompressionLevel.HIGH_QUALITY,
                        badgeText = null,
                        onClick = { viewModel.setCompressionLevel(CompressionLevel.HIGH_QUALITY) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CompressionLevelCard(
                        level = CompressionLevel.BALANCED,
                        isSelected = selectedLevel == CompressionLevel.BALANCED,
                        badgeText = "Recommended",
                        onClick = { viewModel.setCompressionLevel(CompressionLevel.BALANCED) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CompressionLevelCard(
                        level = CompressionLevel.SMALLEST_SIZE,
                        isSelected = selectedLevel == CompressionLevel.SMALLEST_SIZE,
                        badgeText = null,
                        onClick = { viewModel.setCompressionLevel(CompressionLevel.SMALLEST_SIZE) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.compressPdf() },
                        enabled = uiState !is CompressUiState.Processing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compress PDF",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(80.dp)) // Extra padding for bottom navigation
                }
            }

            // Processing Overlay
            if (uiState is CompressUiState.Processing) {
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
                                text = "Compressing PDF...",
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
            if (uiState is CompressUiState.Error) {
                val errorMsg = (uiState as CompressUiState.Error).message
                AlertDialog(
                    onDismissRequest = { viewModel.clearErrorAndKeepState() },
                    title = { Text("Compression Failed", fontWeight = FontWeight.Bold) },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearErrorAndKeepState() }) {
                            Text("OK")
                        }
                    }
                )
            }

            // User Message Alert Dialog
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
private fun CompressionLevelCard(
    level: CompressionLevel,
    isSelected: Boolean,
    badgeText: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.primary))
        } else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = level.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = level.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyCompressPdfSelectionContent(
    onSelectFile: () -> Unit,
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
            text = "No PDF file selected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a PDF file to reduce its size while preserving quality.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSelectFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select PDF File", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SelectedPdfHeaderCard(
    pdfFile: PdfFile,
    onChangeFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                tint = MaterialTheme.colorScheme.primary,
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
            }

            OutlinedButton(
                onClick = onChangeFile,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Change", fontSize = 12.sp)
            }
        }
    }
}
