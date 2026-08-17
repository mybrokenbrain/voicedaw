package com.voicedaw.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun WaveformView(
    amplitudeSamples: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val recordingColor = MaterialTheme.colorScheme.error
    val idleColor = MaterialTheme.colorScheme.outline

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val drawColor = if (!isRecording || amplitudeSamples.isNotEmpty()) {
        if (isRecording) recordingColor else idleColor
    } else {
        recordingColor.copy(alpha = pulseAlpha)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerY = size.height / 2f
        val halfHeight = size.height / 2f

        if (amplitudeSamples.isEmpty()) {
            drawLine(
                color = drawColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        } else {
            val sampleCount = amplitudeSamples.size
            val stepX = size.width / sampleCount

            for (i in 0 until sampleCount) {
                val x = (i * stepX) + (stepX / 2f)
                val amplitude = amplitudeSamples[i].coerceIn(0f, 1f)
                val barHalf = amplitude * halfHeight * 0.9f

                drawLine(
                    color = drawColor,
                    start = Offset(x, centerY - barHalf),
                    end = Offset(x, centerY + barHalf),
                    strokeWidth = maxOf(1.5f, 0.7f * stepX),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
