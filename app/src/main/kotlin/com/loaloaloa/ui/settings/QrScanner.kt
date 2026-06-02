package com.loaloaloa.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.util.concurrent.atomic.AtomicBoolean

/** How long to wait with no decode before showing the "can't read?" hint. */
private const val HINT_DELAY_MS = 6_000L

/**
 * Full-screen, in-app QR scanner that replaces ZXing's stock capture activity. Shows a live camera
 * preview with a Material3 overlay: a dimmed scrim with a rounded viewfinder cutout, an animated
 * scan line, a close button, and a torch toggle. Decodes QR only and fires [onResult] exactly once.
 *
 * Caller must ensure CAMERA permission is granted before showing this (see [RelaySettingsScreen]).
 */
@Composable
fun QrScanner(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    title: String = "Quét mã QR",
    instruction: String = "Đưa mã QR trên máy shop vào khung",
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { AtomicBoolean(false) }
    var torchOn by remember { mutableStateOf(false) }
    // After a few seconds with no decode, surface a hint — most failures are distance/lighting/dirty lens.
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(HINT_DELAY_MS)
        if (!handled.get()) showHint = true
    }

    val barcodeView = remember {
        BarcodeView(context).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    val text = result.text ?: return
                    if (handled.compareAndSet(false, true)) {
                        pause()
                        onResult(text)
                    }
                }
            })
        }
    }

    // Drive the camera off the composition lifecycle so it releases when backgrounded.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeView.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        barcodeView.resume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView.pause()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { barcodeView }, modifier = Modifier.fillMaxSize())

            ViewfinderOverlay()

            // Top bar: close + title, padded below the status bar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Đóng")
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Bottom: instruction + torch toggle, padded above the nav bar.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    instruction,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                if (showHint) {
                    Text(
                        "Chưa nhận được mã? Giữ máy cách mã 15–25cm, nơi đủ sáng và lau sạch ống kính.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                IconButton(
                    onClick = {
                        torchOn = !torchOn
                        barcodeView.setTorch(torchOn)
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (torchOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = 0.18f)
                        },
                        contentColor = if (torchOn) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            Color.White
                        },
                    ),
                ) {
                    Icon(
                        if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = if (torchOn) "Tắt đèn" else "Bật đèn",
                    )
                }
            }
        }
    }
}

/**
 * Draws the dimmed scrim with a centered rounded-square cutout, a primary-colored frame, and a
 * vertically sweeping scan line confined to the cutout. Pure drawing — no camera coupling.
 */
@Composable
private fun ViewfinderOverlay() {
    val frameColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweep",
    )

    Canvas(Modifier.fillMaxSize()) {
        val side = minOf(size.width, size.height) * 0.70f
        val left = (size.width - side) / 2f
        // Bias the window slightly above center so it clears the bottom controls.
        val top = (size.height - side) / 2f - side * 0.06f
        val radiusPx = 22.dp.toPx()
        val corner = CornerRadius(radiusPx, radiusPx)

        // Scrim with an even-odd rounded cutout.
        val scrim = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(Rect(left, top, left + side, top + side), corner))
            fillType = PathFillType.EvenOdd
        }
        drawPath(scrim, color = Color.Black.copy(alpha = 0.58f))

        // Frame border.
        drawRoundRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = corner,
            style = Stroke(width = 3.dp.toPx()),
        )

        // Sweeping scan line with faded ends, confined to the cutout.
        val inset = 10.dp.toPx()
        val lineY = top + inset + sweep * (side - 2 * inset)
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, frameColor, Color.Transparent),
                startX = left + inset,
                endX = left + side - inset,
            ),
            topLeft = Offset(left + inset, lineY - 1.5f.dp.toPx()),
            size = Size(side - 2 * inset, 3.dp.toPx()),
        )
    }
}
