package com.voicedaw.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PunchControls — inline punch-in/out controls for the Record screen.
 *
 * When [enabled] is true, shows editable IN and OUT time fields (in seconds).
 * The punch window is displayed as a colored range indicator.
 *
 * M3 acceptance criterion: "Punch-in/out" (Section 3 of Milestone 3).
 *
 * @param enabled       Whether punch mode is active.
 * @param punchInMs     Punch-in time in milliseconds.
 * @param punchOutMs    Punch-out time in milliseconds.
 * @param onToggle      Called when the PUNCH button is pressed.
 * @param onPunchInMs   Called with new punch-in time (ms) when edited.
 * @param onPunchOutMs  Called with new punch-out time (ms) when edited.
 */
@Composable
fun PunchControls(
    enabled: Boolean,
    punchInMs: Long,
    punchOutMs: Long,
    onToggle: () -> Unit,
    onPunchInMs: (Long) -> Unit,
    onPunchOutMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // PUNCH toggle button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (enabled) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "PUNCH",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
        }

        if (enabled) {
            // IN field
            PunchTimeField(
                label   = "IN",
                valueMs = punchInMs,
                onValueMs = { ms ->
                    // Clamp: IN must be before OUT
                    onPunchInMs(ms.coerceAtMost(punchOutMs - 100L).coerceAtLeast(0L))
                },
            )

            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // OUT field
            PunchTimeField(
                label   = "OUT",
                valueMs = punchOutMs,
                onValueMs = { ms ->
                    // Clamp: OUT must be after IN
                    onPunchOutMs(ms.coerceAtLeast(punchInMs + 100L))
                },
            )

            // Duration display
            val durationSec = (punchOutMs - punchInMs) / 1000f
            Text(
                text = "(${String.format("%.1f", durationSec)}s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        } else {
            Text(
                text = "Tap PUNCH to enable punch-in/out",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun PunchTimeField(
    label: String,
    valueMs: Long,
    onValueMs: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var textValue by remember(valueMs) { mutableStateOf(formatMs(valueMs)) }

    if (editing) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            label = { Text(label, fontSize = 9.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(72.dp),
            textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            trailingIcon = {
                TextButton(
                    onClick = {
                        val sec = textValue.toFloatOrNull()
                        if (sec != null && sec >= 0f) {
                            onValueMs((sec * 1000f).toLong())
                        }
                        editing = false
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(24.dp),
                ) {
                    Text("✓", fontSize = 10.sp)
                }
            },
        )
    } else {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .clickable { editing = true; textValue = formatMs(valueMs) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
            Text(
                text = formatMs(valueMs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
            )
        }
    }
}

/** Format milliseconds as "M:SS.t" for display. */
private fun formatMs(ms: Long): String {
    val sec = ms / 1000f
    return String.format("%.1f", sec)
}
