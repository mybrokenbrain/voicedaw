package com.voicedaw.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

@Composable
fun RecordPermissionEffect(
    snackbarHostState: SnackbarHostState,
    onGranted: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var requested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onGranted()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission required")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!requested) {
            requested = true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
