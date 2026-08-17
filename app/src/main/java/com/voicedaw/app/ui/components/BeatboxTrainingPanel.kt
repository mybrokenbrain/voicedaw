package com.voicedaw.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun BeatboxTrainingPanel(
    currentMode: Int,
    lastDetectedPad: Int,
    onModeChange: (Int) -> Unit,
    onTrainPad: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SegmentedButton(
                text = "Train",
                isSelected = currentMode == 0,
                onClick = { onModeChange(0) }
            )
            SegmentedButton(
                text = "Play",
                isSelected = currentMode == 1,
                onClick = { onModeChange(1) }
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DrumPad(
                label = "Kick",
                padIndex = 0,
                isDetected = lastDetectedPad == 0,
                onTrain = onTrainPad
            )
            DrumPad(
                label = "Snare",
                padIndex = 1,
                isDetected = lastDetectedPad == 1,
                onTrain = onTrainPad
            )
            DrumPad(
                label = "HiHat",
                padIndex = 2,
                isDetected = lastDetectedPad == 2,
                onTrain = onTrainPad
            )
        }
    }
}

@Composable
private fun SegmentedButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text = text)
    }
}

@Composable
private fun DrumPad(
    label: String,
    padIndex: Int,
    isDetected: Boolean,
    onTrain: (Int) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val bgColor = when {
        isPressed -> MaterialTheme.colorScheme.error
        isDetected -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surface
    }
    
    val textColor = if (isPressed || isDetected) Color.White else MaterialTheme.colorScheme.onSurface
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(bgColor, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onTrain(padIndex)
                        tryAwaitRelease()
                        isPressed = false
                        onTrain(-1)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
