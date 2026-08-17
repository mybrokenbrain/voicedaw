package com.voicedaw.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.ui.graphics.vector.ImageVector

sealed class DawDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Record       : DawDestination("record",    "Record",  Icons.Default.GraphicEq)
    object Arrange      : DawDestination("arrange",   "Arrange", Icons.Default.GridView)
    object Mixer        : DawDestination("mixer",     "Mixer",   Icons.Default.Tune)
    object PianoRoll    : DawDestination("piano_roll","Piano Roll",Icons.Default.MusicNote)
    object DrumSequencer: DawDestination("drum_seq",  "Drums",   Icons.Default.Piano)
    object VocalPads    : DawDestination("vocal_pads","Pads",    Icons.Default.GridOn)
    object Projects     : DawDestination("projects",  "Projects",Icons.Default.GridView)
}
