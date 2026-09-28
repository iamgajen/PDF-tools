package com.pdftools.app.ui.sign

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdftools.app.domain.pdf.PdfFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignPdfViewModel = viewModel(factory = SignPdfViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val pdfFile by viewModel.selectedPdfFile.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val signatures by viewModel.signatures.collectAsState()
    val selectedSignatureId by viewModel.selectedSignatureId.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val thumbnails by viewModel.pageThumbnails.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    var showAddSignatureDialog by remember { mutableStateOf(false) }

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

    if (uiState is SignPdfUiState.Success) {
        val successState = uiState as SignPdfUiState.Success
        SignPdfResultScreen(
            outputFile = successState.outputFile,
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
                title = { Text("Sign PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedSignatureId != null) {
                        IconButton(onClick = { viewModel.deleteSelectedSignature() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Signature",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
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
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sign PDF Document",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select a PDF file to place your drawn, typed, or imported signature on any page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { selectPdfLauncher.launch(arrayOf("application/pdf")) },
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
            } else {
                val totalPages = currentPdf.pageCount ?: 1
                val currentPageBitmap = thumbnails[currentPageIndex]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // PDF Info Header Card
                    SelectedPdfHeaderCard(
                        pdfFile = currentPdf,
                        currentPage = currentPageIndex + 1,
                        totalPages = totalPages,
                        onChangeFile = { selectPdfLauncher.launch(arrayOf("application/pdf")) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Page Navigation Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.setCurrentPageIndex(currentPageIndex - 1) },
                                enabled = currentPageIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                            }
                            Text(
                                text = "Page ${currentPageIndex + 1} of $totalPages",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { viewModel.setCurrentPageIndex(currentPageIndex + 1) },
                                enabled = currentPageIndex < totalPages - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                            }
                        }

                        Button(
                            onClick = { showAddSignatureDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Signature", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // PDF Page Preview & Signature Interactive Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray.copy(alpha = 0.1f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable { viewModel.selectSignature(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentPageBitmap != null) {
                            var containerSize by remember { mutableStateOf(IntSize.Zero) }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(currentPageBitmap.width.toFloat() / currentPageBitmap.height.toFloat())
                                    .onGloballyPositioned { containerSize = it.size }
                            ) {
                                Image(
                                    bitmap = currentPageBitmap.asImageBitmap(),
                                    contentDescription = "Page ${currentPageIndex + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Render signatures on current page
                                val pageSignatures = signatures.filter { it.pageIndex == currentPageIndex }
                                for (sig in pageSignatures) {
                                    InteractiveSignatureOverlay(
                                        signature = sig,
                                        isSelected = selectedSignatureId == sig.id,
                                        containerSize = containerSize,
                                        onSelect = { viewModel.selectSignature(sig.id) },
                                        onMove = { nx, ny -> viewModel.updateSignaturePosition(sig.id, nx, ny) },
                                        onResize = { nw -> viewModel.updateSignatureSize(sig.id, nw) },
                                        onRotate = { viewModel.rotateSignature(sig.id, 15f) },
                                        onDelete = { viewModel.deleteSignature(sig.id) }
                                    )
                                }
                            }
                        } else {
                            CircularProgressIndicator()
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Page Thumbnails Strip
                    if (totalPages > 1) {
                        Text(
                            text = "PAGES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(totalPages) { idx ->
                                val thumb = thumbnails[idx]
                                val isCurrent = idx == currentPageIndex
                                ThumbnailItemCard(
                                    pageIndex = idx,
                                    bitmap = thumb,
                                    isSelected = isCurrent,
                                    onClick = { viewModel.setCurrentPageIndex(idx) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Save Action Button
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            val signatureCount = signatures.size
                            Button(
                                onClick = { viewModel.signPdf() },
                                enabled = signatureCount > 0 && uiState !is SignPdfUiState.Saving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (signatureCount > 0) "Save Signed PDF ($signatureCount ${if (signatureCount == 1) "signature" else "signatures"})" else "Add a Signature to Save",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // Extra bottom padding so floating bar doesn't obscure content or bottom nav
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }

            // Dialog for Adding Signature
            if (showAddSignatureDialog) {
                AddSignatureDialog(
                    onDismissRequest = { showAddSignatureDialog = false },
                    onSignatureCreated = { bitmap ->
                        viewModel.addSignature(bitmap)
                    }
                )
            }

            // Saving Progress Dialog
            if (uiState is SignPdfUiState.Saving) {
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
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Saving signed PDF...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Applying signature overlay on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error Alert Dialog
            if (uiState is SignPdfUiState.Error || userMessage != null) {
                val errorMsg = (uiState as? SignPdfUiState.Error)?.message ?: userMessage ?: "An error occurred"
                AlertDialog(
                    onDismissRequest = {
                        viewModel.clearUserMessage()
                        viewModel.clearErrorAndKeepState()
                    },
                    title = { Text("Notice") },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearUserMessage()
                                viewModel.clearErrorAndKeepState()
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun InteractiveSignatureOverlay(
    signature: PlacedSignature,
    isSelected: Boolean,
    containerSize: IntSize,
    onSelect: () -> Unit,
    onMove: (nx: Float, ny: Float) -> Unit,
    onResize: (nw: Float) -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit
) {
    if (containerSize.width <= 0 || containerSize.height <= 0) return

    val density = LocalDensity.current
    val boxWidthPx = containerSize.width.toFloat()
    val boxHeightPx = containerSize.height.toFloat()

    val leftPx = signature.normalizedX * boxWidthPx
    val topPx = signature.normalizedY * boxHeightPx
    val widthPx = signature.normalizedWidth * boxWidthPx
    val heightPx = signature.normalizedHeight * boxHeightPx

    val leftDp = with(density) { leftPx.toDp() }
    val topDp = with(density) { topPx.toDp() }
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    Box(
        modifier = Modifier
            .offset(x = leftDp, y = topDp)
            .size(width = widthDp, height = heightDp)
            .pointerInput(signature.id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newNx = (leftPx + dragAmount.x) / boxWidthPx
                        val newNy = (topPx + dragAmount.y) / boxHeightPx
                        onMove(newNx, newNy)
                    }
                )
            }
            .clickable { onSelect() }
            .rotate(signature.rotationDegrees)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                } else Modifier
            )
    ) {
        Image(
            bitmap = signature.bitmap.asImageBitmap(),
            contentDescription = "Signature",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        if (isSelected) {
            // Delete button on Top-Right
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(22.dp)
                    .clickable { onDelete() }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .padding(3.dp)
                        .fillMaxSize()
                )
            }

            // Rotate button on Top-Left
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-8).dp, y = (-8).dp)
                    .size(22.dp)
                    .clickable { onRotate() }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "Rotate",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(3.dp)
                        .fillMaxSize()
                )
            }

            // Resize handle on Bottom-Right
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(24.dp)
                    .pointerInput(signature.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newW = (widthPx + dragAmount.x) / boxWidthPx
                            onResize(newW)
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Resize",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ThumbnailItemCard(
    pageIndex: Int,
    bitmap: Bitmap?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(60.dp)
            .height(80.dp)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Thumbnail Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun SelectedPdfHeaderCard(
    pdfFile: PdfFile,
    currentPage: Int,
    totalPages: Int,
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
                    Text(
                        text = " • Page $currentPage of $totalPages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
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
