package com.pinyinpeek.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pinyinpeek.dictionary.DictionaryScreen
import com.pinyinpeek.dictionary.DictionaryViewModel
import com.pinyinpeek.translation.TranslationEngineType

private val GLOW = Color(0xFF7EF29A)
private val CARD_BG = Color(0xDD000000)

@Composable
fun CameraScreen(
    cameraVm: CameraViewModel = viewModel(),
    dictVm: DictionaryViewModel = viewModel(),
) {
    val state by cameraVm.state.collectAsStateWithLifecycle()
    val showDict by cameraVm.showDict.collectAsStateWithLifecycle()

    if (showDict) {
        DictionaryScreen(onBack = { cameraVm.toggleDict() }, viewModel = dictVm)
        return
    }

    if (state.isPreview) {
        PreviewMode(cameraVm, state)
    } else {
        FrozenMode(state, cameraVm)
    }
}

// ---------- preview ----------

@Composable
private fun PreviewMode(cameraVm: CameraViewModel, state: AppState) {
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val captureHolder = remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            PreviewView(ctx).also { view ->
                view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                ProcessCameraProvider.getInstance(ctx).addListener({
                    try { val p = ProcessCameraProvider.getInstance(ctx).get(); p.unbindAll()
                        val preview = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                        p.bindToLifecycle(lifecycleOwner, CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build(), preview, capture)
                        captureHolder.value = capture
                    } catch (e: Exception) { Log.e("PP", "cam init", e) }
                }, mainExecutor)
            }
        })
        if (!isCapturing) Button(onClick = {
            captureHolder.value?.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) { cameraVm.captureFrame(image) }
                override fun onError(e: ImageCaptureException) { isCapturing = false }
            }); isCapturing = true
        }, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(72.dp).clip(CircleShape), contentPadding = PaddingValues(0.dp)
        ) { Icon(Icons.Default.PhotoCamera, "Capture", modifier = Modifier.fillMaxSize().padding(8.dp)) }
        if (isCapturing) CircularProgressIndicator(Modifier.align(Alignment.Center), color = GLOW)
        var showSettings by remember { mutableStateOf(false) }
        Row(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            FilledTonalButton(onClick = { cameraVm.toggleDict() }) { Icon(Icons.AutoMirrored.Filled.LibraryBooks, "Dict", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }
        if (showSettings) {
            EngineSettingsDialog(
                current = state.engineType,
                onSelect = { cameraVm.setEngine(it); showSettings = false },
                onDismiss = { showSettings = false },
            )
        }
    }
}

// ---------- frozen + selection + results ----------

@Composable
private fun FrozenMode(state: AppState, cameraVm: CameraViewModel) {
    // Track the frozen image view size for coordinate mapping
    var viewWidth by remember { mutableStateOf(0) }
    var viewHeight by remember { mutableStateOf(0) }

    // Drag state (view coordinates)
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragEnd by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { val s = it.size; if (s.width > 0) { viewWidth = s.width; viewHeight = s.height } }
    ) {
        // Frozen capture image
        state.capturedBitmap?.let { bmp ->
            Image(bitmap = bmp.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                alpha = if (state.isProcessing || state.recognizedText.isNotEmpty()) 0.45f else 0.8f)
        }

        if (isDragging || state.hasSelection) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val l = minOf(dragStart.x, dragEnd.x).toFloat()
                val t = minOf(dragStart.y, dragEnd.y).toFloat()
                val r = maxOf(dragStart.x, dragEnd.x).toFloat()
                val b = maxOf(dragStart.y, dragEnd.y).toFloat()
                if (r - l > 2f || b - t > 2f) {
                    drawRect(color = Color(0x227EF29A), topLeft = Offset(l, t),
                        size = Size(r - l, b - t))
                    drawRect(color = GLOW, topLeft = Offset(l, t),
                        size = Size(r - l, b - t), style = Stroke(width = 2f))
                }
            }
        }

        // Drag gesture on the frozen image
        if (!state.hasSelection && !state.isProcessing) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragStart = it; dragEnd = it; isDragging = true },
                    onDrag = { _, delta -> dragEnd = Offset(dragEnd.x + delta.x, dragEnd.y + delta.y) },
                    onDragEnd = {
                        isDragging = false
                        // Send selection to ViewModel
                        val l = minOf(dragStart.x, dragEnd.x); val t = minOf(dragStart.y, dragEnd.y)
                        val r = maxOf(dragStart.x, dragEnd.x); val b = maxOf(dragStart.y, dragEnd.y)
                        if ((r - l) > 20f && (b - t) > 20f) {
                            cameraVm.onSelection(l, t, r, b, viewWidth, viewHeight)
                        }
                    },
                    onDragCancel = { isDragging = false },
                )
            })
        }

        // Tap anywhere outside the popup to dismiss and reselect. Placed
        // UNDER the popup card so the card's own buttons stay tappable.
        if (state.recognizedText.isNotEmpty()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { cameraVm.reselect() }
            })
        }

        // ---- Character-level pinyin popup ----
        if (state.recognizedText.isNotEmpty()) {
            // Position: below the selection rectangle
            val l = minOf(state.selLeft, state.selRight)
            val t = minOf(state.selTop, state.selBottom)
            val r = maxOf(state.selLeft, state.selRight)
            val b = maxOf(state.selTop, state.selBottom)

            // Map selection to view coords
            val scale = minOf(viewWidth.toFloat() / state.imageWidth, viewHeight.toFloat() / state.imageHeight)
            val offsetX = (viewWidth - state.imageWidth * scale) / 2f
            val offsetY = (viewHeight - state.imageHeight * scale) / 2f

            val viewBottom = b * scale + offsetY
            val viewLeft = l * scale + offsetX

            Box(
                modifier = Modifier
                    .offset { IntOffset((viewLeft.coerceAtLeast(8f)).toInt(), (viewBottom + 12f).toInt()) }
                    .widthIn(max = 320.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CARD_BG),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        // Character-by-character pinyin: each char is a column
                        // with pinyin above and the character below
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            state.charPinyins.forEach { pc ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (pc.pinyin.isNotEmpty()) {
                                        Text(pc.pinyin, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                            color = GLOW, fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center)
                                    }
                                    Text(pc.char.toString(), fontSize = 22.sp,
                                        color = Color.White, fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center)
                                }
                            }
                        }

                        // Translation
                        if (state.translation.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(state.translation, fontSize = 14.sp, color = Color(0xFFBBBBBB),
                                lineHeight = 20.sp)
                        }

                        // Action buttons
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { cameraVm.saveWord() }, enabled = !state.saved) {
                                Text(if (state.saved) "Saved" else "Save", fontSize = 11.sp,
                                    color = if (state.saved) Color.Gray else GLOW)
                            }
                            TextButton(onClick = { cameraVm.openInPleco() }) {
                                Text("Pleco", fontSize = 11.sp, color = GLOW)
                            }
                            TextButton(onClick = { cameraVm.reselect() }) {
                                Text("Reselect", fontSize = 11.sp, color = Color(0xFFBBBBBB))
                            }
                            TextButton(onClick = { cameraVm.retake() }) {
                                Text("Retake", fontSize = 11.sp, color = Color(0xFF888888))
                            }
                        }
                    }
                }
            }
        }

        // Processing indicator
        if (state.isProcessing) CircularProgressIndicator(Modifier.align(Alignment.Center), color = GLOW)

        // Engine switcher, reachable from the frozen view without retaking.
        // Placed after the full-screen tap-catcher so it stays tappable.
        var showEngineSettings by remember { mutableStateOf(false) }
        FilledTonalButton(
            onClick = { showEngineSettings = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Settings, "Translation engine", tint = Color.White)
        }
        if (showEngineSettings) {
            EngineSettingsDialog(
                current = state.engineType,
                onSelect = { cameraVm.setEngine(it); showEngineSettings = false },
                onDismiss = { showEngineSettings = false },
            )
        }


        // "Tap and drag to select text" hint when frozen with no action yet
        if (!state.hasSelection && !state.isProcessing && state.recognizedText.isEmpty()) {
            Box(Modifier.align(Alignment.TopCenter).padding(24.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = CARD_BG), shape = RoundedCornerShape(8.dp)) {
                    Text("Drag a rectangle around the text to translate", modifier = Modifier.padding(12.dp),
                        color = Color.White, fontSize = 13.sp)
                }
            }
            // Also show retake button
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                TextButton(onClick = { cameraVm.retake() }) {
                    Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("Retake", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EngineSettingsDialog(
    current: TranslationEngineType,
    onSelect: (TranslationEngineType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Translation engine") },
        text = {
            Column {
                TranslationEngineType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(type) }.padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = current == type, onClick = { onSelect(type) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(type.displayName, fontWeight = if (current == type) FontWeight.Bold else FontWeight.Normal)
                            Text(engineDescription(type), fontSize = 11.sp, color = Color(0xFF999999))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

private fun engineDescription(type: TranslationEngineType): String = when (type) {
    TranslationEngineType.DICTIONARY -> "Word-by-word definitions, fully offline"
    TranslationEngineType.GOOGLE_MT -> "Google ML Kit neural translation (downloads ~30 MB)"
    TranslationEngineType.HANLP -> "HanLP word segmentation + pinyin + dictionary (offline)"
}
