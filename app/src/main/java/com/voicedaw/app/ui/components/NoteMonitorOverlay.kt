package com.voicedaw.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

private val NOTE_NAMES = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

private fun hzToMidi(hz: Float): Int =
    (12.0 * log2(hz.toDouble() / 440.0) + 69.0).roundToInt().coerceIn(0, 127)

private fun midiToName(midi: Int): String {
    val octave = (midi / 12) - 1
    return "${NOTE_NAMES[midi % 12]}$octave"
}

private fun hzToCents(hz: Float): Float {
    if (hz <= 0f) return 0f
    val exactMidi   = 12.0 * log2(hz.toDouble() / 440.0) + 69.0
    val nearestMidi = exactMidi.roundToInt()
    return ((exactMidi - nearestMidi) * 100.0).toFloat().coerceIn(-50f, 50f)
}

@Composable
fun NoteMonitorOverlay(
    visible: Boolean,
    pitchHz: Float,
    pitchAmp: Float,
    ampFloor: Float,
    isRecording: Boolean,
    onToggleRecord: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.96f),
        exit    = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.96f),
    ) {
        val hasSignal = pitchHz > 60f && pitchAmp > ampFloor
        val midi      = if (hasSignal) hzToMidi(pitchHz) else -1
        val noteName  = if (midi >= 0) midiToName(midi) else "---"
        val cents     = if (hasSignal) hzToCents(pitchHz) else 0f
        val stable    = hasSignal && abs(cents) < 15f

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xCC0A0A12)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                // Stability chip
                val chipColor = when {
                    !hasSignal -> Color(0xFF444444)
                    stable     -> Color(0xFF00C853)
                    else       -> Color(0xFFFFAB00)
                }
                val chipLabel = when {
                    !hasSignal -> "LISTENING"
                    stable     -> "LOCKED"
                    else       -> "TUNING"
                }
                Surface(
                    shape    = RoundedCornerShape(50),
                    color    = chipColor.copy(alpha = 0.2f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(chipColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(chipLabel, color = chipColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Note name
                Text(
                    text       = noteName,
                    color      = Color.White,
                    fontSize   = 96.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 96.sp,
                )

                // Cents bar
                CentsBar(cents = cents, stable = stable)

                // Hz readout
                Text(
                    text  = if (hasSignal) "%.1f Hz".format(pitchHz) else "--- Hz",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                )

                Spacer(Modifier.height(16.dp))

                // Transport
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = onStop,
                        colors  = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF37474F)),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White,
                             modifier = Modifier.size(28.dp))
                    }
                    FilledIconButton(
                        onClick = onToggleRecord,
                        colors  = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isRecording) Color(0xFFB71C1C) else Color(0xFFD32F2F)
                        ),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop Recording" else "Record",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CentsBar(cents: Float, stable: Boolean) {
    val needleColor by androidx.compose.runtime.derivedStateOf {
        if (stable) Color(0xFF00C853) else Color(0xFFFFAB00)
    }
    val animCents by animateFloatAsState(
        targetValue = cents,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "cents",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)) {
            val w  = size.width
            val cx = w / 2f
            val cy = size.height * 0.75f

            drawLine(Color(0xFF444444), Offset(0f, cy), Offset(w, cy),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

            val zoneHalf = w * 0.15f
            drawLine(Color(0xFF00C853).copy(alpha = 0.3f),
                Offset(cx - zoneHalf, cy), Offset(cx + zoneHalf, cy),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

            val needleX = cx + (animCents / 50f) * (w / 2f)
            drawLine(needleColor, Offset(needleX, 4.dp.toPx()), Offset(needleX, cy + 4.dp.toPx()),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text  = if (cents == 0f) "+/-0c" else "%+.0fc".format(cents),
            color = needleColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
