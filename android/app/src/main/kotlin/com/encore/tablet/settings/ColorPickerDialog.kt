package com.encore.tablet.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Full-screen color picker dialog.
 *
 * Shows:
 *  - Hue ring (drag to change hue)
 *  - SV square (drag to change saturation/value)
 *  - Preview swatch comparing before/after
 *  - Hex input field (synced bidirectionally with the picker)
 *
 * @param initialHex  Starting color as a #RRGGBB string.
 * @param onDismiss   Called when the user taps Cancel.
 * @param onConfirm   Called with the final #RRGGBB string when the user taps OK.
 */
@Composable
fun ColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Parse initial color into HSV
    val initialHsv = floatArrayOf(0f, 0f, 0f).also { hsv ->
        runCatching { AndroidColor.colorToHSV(AndroidColor.parseColor(initialHex), hsv) }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    // Hex field state — updated when picker moves, or drives picker when typed
    var hexInput by remember { mutableStateOf(initialHex) }
    var hexError by remember { mutableStateOf(false) }

    fun currentHex(): String {
        val rgb = FloatArray(3).let {
            AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
        }
        return "#%06X".format(rgb and 0xFFFFFF)
    }

    fun syncHexFromPicker() {
        hexInput = currentHex()
        hexError = false
    }

    fun tryApplyHex(v: String) {
        hexInput = v
        if (v.length == 7 && v.startsWith("#")) {
            runCatching {
                val parsed = floatArrayOf(0f, 0f, 0f)
                AndroidColor.colorToHSV(AndroidColor.parseColor(v), parsed)
                hue = parsed[0]; sat = parsed[1]; value = parsed[2]
                hexError = false
            }.onFailure { hexError = true }
        }
    }

    val pickerColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hue ring + SV square stacked
                Box(contentAlignment = Alignment.Center) {
                    HueRing(
                        hue = hue,
                        modifier = Modifier.size(220.dp),
                        onHueChange = { h -> hue = h; syncHexFromPicker() }
                    )
                    SatValSquare(
                        hue = hue,
                        sat = sat,
                        value = value,
                        modifier = Modifier.size(130.dp),
                        onSatValChange = { s, v -> sat = s; value = v; syncHexFromPicker() }
                    )
                }

                // Before / after swatch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                            .background(Color(AndroidColor.parseColor(
                                initialHex.takeIf { it.length == 7 && it.startsWith("#") } ?: "#888888"
                            )))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                            .background(pickerColor)
                    )
                }

                // Hex input
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = ::tryApplyHex,
                    label = { Text("Hex") },
                    singleLine = true,
                    isError = hexError,
                    modifier = Modifier.width(160.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                        .copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (!hexError) onConfirm(hexInput)
                    })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentHex()) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Hue Ring ──────────────────────────────────────────────────────────────────

@Composable
private fun HueRing(
    hue: Float,
    modifier: Modifier = Modifier,
    onHueChange: (Float) -> Unit,
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = change.position.x - center.x
                    val dy = change.position.y - center.y
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    onHueChange(angle)
                }
                detectTapGestures { pos ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = pos.x - center.x
                    val dy = pos.y - center.y
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    onHueChange(angle)
                }
            }
    ) {
        val ringWidth = size.minDimension * 0.1f
        val radius = size.minDimension / 2f - ringWidth / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Draw hue ring as 360 arc segments
        val segCount = 360
        val sweep = 360f / segCount
        for (i in 0 until segCount) {
            drawArc(
                color = Color(AndroidColor.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))),
                startAngle = i.toFloat() - 90f,
                sweepAngle = sweep + 0.5f,
                useCenter = false,
                style = Stroke(width = ringWidth),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
        }

        // Thumb indicator
        val thumbAngleRad = Math.toRadians((hue - 90.0))
        val tx = center.x + radius * cos(thumbAngleRad).toFloat()
        val ty = center.y + radius * sin(thumbAngleRad).toFloat()
        drawCircle(color = Color.White, radius = ringWidth * 0.65f, center = Offset(tx, ty))
        drawCircle(
            color = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
            radius = ringWidth * 0.45f,
            center = Offset(tx, ty)
        )
    }
}

// ── Saturation / Value Square ─────────────────────────────────────────────────

@Composable
private fun SatValSquare(
    hue: Float,
    sat: Float,
    value: Float,
    modifier: Modifier = Modifier,
    onSatValChange: (sat: Float, value: Float) -> Unit,
) {
    val pureHue = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    Box(modifier = modifier
        .clip(RoundedCornerShape(4.dp))
        .pointerInput(Unit) {
            fun handle(pos: Offset) {
                val s = (pos.x / size.width).coerceIn(0f, 1f)
                val v = (1f - pos.y / size.height).coerceIn(0f, 1f)
                onSatValChange(s, v)
            }
            detectDragGestures(
                onDragStart = { handle(it) },
                onDrag = { change, _ -> change.consume(); handle(change.position) }
            )
            detectTapGestures { handle(it) }
        }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // White → hue gradient (saturation)
            drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
            // Transparent → black gradient (value)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        // Thumb
        val thumbX = sat
        val thumbY = 1f - value
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = (thumbX * 130 - 7).coerceAtLeast(0f).dp,
                    top   = (thumbY * 130 - 7).coerceAtLeast(0f).dp
                )
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}
