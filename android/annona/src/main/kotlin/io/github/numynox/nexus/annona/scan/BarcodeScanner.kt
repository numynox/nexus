package io.github.numynox.nexus.annona.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import zxingcpp.BarcodeReader

/**
 * The camera, reading retail barcodes (EAN-13/8, UPC) on the device with
 * zxing-cpp — the native counterpart of the web's html5-qrcode scanner, and
 * much quicker to lock on. [onScanned] fires once per distinct code; the
 * caller decides what happens next.
 */
@Composable
fun BarcodeScanner(onScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    Box(modifier.clip(MaterialTheme.shapes.extraLarge).background(Color.Black), contentAlignment = Alignment.Center) {
        if (granted) {
            CameraPreview(onScanned)
        } else {
            CameraNotice(
                icon = Icons.Rounded.PhotoCamera,
                text = "Annona reads barcodes with the camera. Pictures never leave the phone.",
                action = "Allow camera",
                onAction = { launcher.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val callback by rememberUpdatedState(onScanned)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val reader = BarcodeReader(BarcodeReader.Options().apply {
            formats = setOf(BarcodeReader.Format.ALL_RETAIL)
            tryHarder = true
            tryRotate = true
        })
        var last: String? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    image.use { frame ->
                        val code = runCatching { reader.read(frame) }.getOrNull()?.firstOrNull { !it.text.isNullOrBlank() }?.text
                        if (code != null && code != last) {
                            last = code
                            ContextCompat.getMainExecutor(context).execute {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                callback(code)
                            }
                        }
                    }
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { failed = true }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            // Never block the main thread on a provider still starting up;
            // if it is not ready, it bound nothing to unbind.
            if (future.isDone) runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }

    if (failed) {
        CameraNotice(Icons.Rounded.NoPhotography, "No camera is available. Type the barcode or a name below instead.", null, null)
        return
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScanWindow(Modifier.fillMaxSize())
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            FilledTonalIconToggleButton(
                checked = torch,
                onCheckedChange = {
                    torch = it
                    camera?.cameraControl?.enableTorch(it)
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Icon(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, contentDescription = "Torch")
            }
        }
    }
}

/** Darkens around a barcode-shaped window, so it is obvious where to aim. */
@Composable
private fun ScanWindow(modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val width = size.width * 0.78f
        val height = width * 0.45f
        val topLeft = Offset((size.width - width) / 2, (size.height - height) / 2)
        val corner = CornerRadius(20.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.45f))
        drawRoundRect(Color.Transparent, topLeft, Size(width, height), corner, blendMode = BlendMode.Clear)
        drawRoundRect(accent, topLeft, Size(width, height), corner, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun CameraNotice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: String?, onAction: (() -> Unit)?) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (action != null && onAction != null) FilledTonalButton(onClick = onAction) { Text(action) }
    }
}
