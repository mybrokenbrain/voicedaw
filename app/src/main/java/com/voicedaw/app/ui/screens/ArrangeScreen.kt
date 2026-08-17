package com.voicedaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicedaw.audioengine.AudioEngineViewModel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.voicedaw.audioengine.recording.effectiveDurationFrames

@Composable
fun ArrangeScreen(
    engineVm: AudioEngineViewModel? = null,
) {
    if (engineVm == null) return

    val state by engineVm.state.collectAsState()
    val tracks = state.tracks
    val playbackPositionMs = state.playbackPositionMs

    // Zoom/Scroll State
    var scrollX by remember { mutableFloatStateOf(0f) }
    var scaleX by remember { mutableFloatStateOf(10f) } // ms per pixel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Arrange", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { engineVm.toggleRecord() }) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = "Record",
                    tint = if (state.isRecording) Color.Red else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { if (state.isPlaying) engineVm.pause() else engineVm.play() }) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Timeline
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Track Headers
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                tracks.forEach { track ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(track.name, modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Timeline Canvas
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(Color(0xFF1E1E1E))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            scrollX -= dragAmount.x
                            if (scrollX < 0f) scrollX = 0f
                        }
                    }
            ) {
                val clipColor = MaterialTheme.colorScheme.secondaryContainer
                val playheadColor = Color.Red

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val trackHeight = 80.dp.toPx()

                    tracks.forEachIndexed { i, track ->
                        val y = i * trackHeight
                        drawLine(
                            color = Color.DarkGray,
                            start = Offset(0f, y + trackHeight),
                            end = Offset(size.width, y + trackHeight),
                            strokeWidth = 1f
                        )

                        track.activeTake?.let { take ->
                            val startX = (take.startSample / state.sampleRate.toFloat() * 1000f - scrollX) / scaleX
                            val durationFrames = take.effectiveDurationFrames(state.sampleRate)
                            val width = durationFrames / state.sampleRate.toFloat() * 1000f / scaleX
                            if (startX + width > 0 && startX < size.width) {
                                drawRoundRect(
                                    color = clipColor,
                                    topLeft = Offset(startX, y + 10f),
                                    size = androidx.compose.ui.geometry.Size(width, trackHeight - 20f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                    }

                    val playheadX = (playbackPositionMs - scrollX) / scaleX
                    if (playheadX in 0f..size.width) {
                        drawLine(
                            color = playheadColor,
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, size.height),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }
    }
}
