package com.voicedaw.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.content.ContextCompat
import com.voicedaw.app.ui.VoiceDawApp
import com.voicedaw.app.ui.theme.VoiceDawTheme
import com.voicedaw.audioengine.AudioEngineViewModel

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.util.Date

class MainActivity : ComponentActivity() {

    private val audioEngineViewModel: AudioEngineViewModel by viewModels()

    private val requestRecordAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            audioEngineViewModel.restartEngine()
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(getExternalFilesDir(null), "crash_log.txt")
                FileWriter(crashFile, true).use { writer ->
                    writer.write("--- Crash at ${Date()} on thread ${thread.name} ---\n")
                    writer.write(throwable.stackTraceToString())
                    writer.write("\n\n")
                }
                Log.e("VoiceDaw", "CRASH SAVED TO FILE", throwable)
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            VoiceDawTheme {
                VoiceDawApp(windowSizeClass = windowSizeClass)
            }
        }
    }
}
