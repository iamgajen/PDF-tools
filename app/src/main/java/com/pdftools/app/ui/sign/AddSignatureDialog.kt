package com.pdftools.app.ui.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class StrokePath(
    val path: Path,
    val strokeWidth: Float = 8f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSignatureDialog(
    onDismissRequest: () -> Unit,
    onSignatureCreated: (Bitmap) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Draw, 1: Type, 2: Image
    val tabs = listOf("Draw", "Type", "Image")

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Signature",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selector
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Based on Selected Tab
                when (selectedTab) {
                    0 -> DrawSignatureTab(
                        onDismiss = onDismissRequest,
                        onDone = { bitmap ->
                            onSignatureCreated(bitmap)
                            onDismissRequest()
                        }
                    )
                    1 -> TypeSignatureTab(
                        onDismiss = onDismissRequest,
                        onDone = { bitmap ->
                            onSignatureCreated(bitmap)
                            onDismissRequest()
                        }
                    )
                    2 -> ImageSignatureTab(
                        onDismiss = onDismissRequest,
                        onDone = { bitmap ->
                            onSignatureCreated(bitmap)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawSignatureTab(
    onDismiss: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    val paths = remember { mutableStateListOf<StrokePath>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Draw your signature using touch",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Drawing Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val p = Path().apply { moveTo(offset.x, offset.y) }
                                currentPath = p
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPath?.let { p ->
                                    val currentPos = change.position
                                    p.lineTo(currentPos.x, currentPos.y)
                                    currentPath = Path().apply { addPath(p) }
                                }
                            },
                            onDragEnd = {
                                currentPath?.let { p ->
                                    paths.add(StrokePath(p, strokeWidth))
                                    currentPath = null
                                }
                            }
                        )
                    }
            ) {
                // Draw existing paths
                paths.forEach { strokePath ->
                    drawPath(
                        path = strokePath.path,
                        color = Color.Black,
                        style = Stroke(width = strokePath.strokeWidth)
                    )
                }
                // Draw currently active path
                currentPath?.let { p ->
                    drawPath(
                        path = p,
                        color = Color.Black,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }

            // Canvas Watermark / Hint
            if (paths.isEmpty() && currentPath == null) {
                Text(
                    text = "Sign here",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Controls Row: Stroke Width & Actions (Undo, Clear)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Thickness: ", style = MaterialTheme.typography.labelMedium)
                listOf(5f to "Thin", 9f to "Medium", 15f to "Thick").forEach { (width, label) ->
                    FilterChip(
                        selected = strokeWidth == width,
                        onClick = { strokeWidth = width },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            Row {
                IconButton(
                    onClick = {
                        if (paths.isNotEmpty()) paths.removeAt(paths.size - 1)
                    },
                    enabled = paths.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(
                    onClick = { paths.clear() },
                    enabled = paths.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Dialog Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val bitmap = renderPathsToBitmap(paths)
                    if (bitmap != null) {
                        onDone(bitmap)
                    }
                },
                enabled = paths.isNotEmpty()
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun TypeSignatureTab(
    onDismiss: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    var typedText by remember { mutableStateOf("") }
    var fontStyleIndex by remember { mutableIntStateOf(0) }

    val fontStyles = listOf("Handwritten / Cursive", "Serif", "Sans-Serif")

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = typedText,
            onValueChange = { typedText = it },
            label = { Text("Enter your name") },
            placeholder = { Text("e.g. John Doe") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Font Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fontStyles.forEachIndexed { index, name ->
                FilterChip(
                    selected = fontStyleIndex == index,
                    onClick = { fontStyleIndex = index },
                    label = { Text(name, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preview Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (typedText.isNotBlank()) {
                val previewBitmap = remember(typedText, fontStyleIndex) {
                    renderTypedTextToBitmap(typedText, fontStyleIndex, 48f)
                }
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Signature Preview",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Text(
                    text = "Signature preview will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val bitmap = renderTypedTextToBitmap(typedText, fontStyleIndex, 64f)
                    if (bitmap != null) {
                        onDone(bitmap)
                    }
                },
                enabled = typedText.isNotBlank()
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ImageSignatureTab(
    onDismiss: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var decodedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val rawBmp = BitmapFactory.decodeStream(stream, null, options)
                    if (rawBmp != null) {
                        decodedBitmap = trimTransparentPixels(rawBmp)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (decodedBitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Import signature image from device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose Image")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = decodedBitmap!!.asImageBitmap(),
                    contentDescription = "Imported Signature",
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("Change Image")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    decodedBitmap?.let { onDone(it) }
                },
                enabled = decodedBitmap != null
            ) {
                Text("Done")
            }
        }
    }
}

private fun renderPathsToBitmap(strokePaths: List<StrokePath>): Bitmap? {
    if (strokePaths.isEmpty()) return null

    val targetWidth = 1000
    val targetHeight = 500
    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    strokePaths.forEach { strokePath ->
        paint.strokeWidth = strokePath.strokeWidth * 2.5f
        val androidPath = android.graphics.Path()
        androidPath.addPath(strokePath.path.asAndroidPath())
        canvas.drawPath(androidPath, paint)
    }

    return trimTransparentPixels(bitmap)
}

private fun renderTypedTextToBitmap(
    text: String,
    fontStyleIndex: Int,
    fontSizeSp: Float
): Bitmap? {
    if (text.isBlank()) return null

    val paint = Paint().apply {
        color = AndroidColor.BLACK
        isAntiAlias = true
        textSize = fontSizeSp * 2.5f
        typeface = when (fontStyleIndex) {
            0 -> Typeface.create("cursive", Typeface.ITALIC)
            1 -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }
    }

    val textBounds = Rect()
    paint.getTextBounds(text, 0, text.length, textBounds)

    val width = (paint.measureText(text) + 40).toInt().coerceAtLeast(100)
    val height = (textBounds.height() + 60).coerceAtLeast(60)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val x = 20f
    val y = (height / 2f) - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(text, x, y, paint)

    return trimTransparentPixels(bitmap)
}

private fun trimTransparentPixels(bitmap: Bitmap, paddingDp: Int = 10): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val alpha = (pixels[y * width + x] shr 24) and 0xFF
            if (alpha > 10) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }

    if (maxX < minX || maxY < minY) {
        return bitmap
    }

    val pad = paddingDp.coerceAtLeast(0)
    val startX = (minX - pad).coerceAtLeast(0)
    val startY = (minY - pad).coerceAtLeast(0)
    val endX = (maxX + pad).coerceAtMost(width - 1)
    val endY = (maxY + pad).coerceAtMost(height - 1)

    val croppedWidth = endX - startX + 1
    val croppedHeight = endY - startY + 1

    return Bitmap.createBitmap(bitmap, startX, startY, croppedWidth, croppedHeight)
}
