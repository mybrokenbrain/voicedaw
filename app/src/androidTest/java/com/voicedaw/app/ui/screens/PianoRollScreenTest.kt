package com.voicedaw.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voicedaw.audioengine.AudioEngineViewModel
import com.voicedaw.midi.MidiViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PianoRollScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun voiceInputButton_togglesState() {
        // We test with real ViewModels to verify end-to-end integration
        // (Oboe engine will initialize in the background)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val engineVm = AudioEngineViewModel(context)
        val midiVm = MidiViewModel(context)

        composeTestRule.setContent {
            PianoRollScreen(engineVm = engineVm, midiVm = midiVm)
        }

        // Initially, the button should say "Voice Input"
        composeTestRule.onNodeWithText("Voice Input").assertExists()

        // Click to start voice capture
        composeTestRule.onNodeWithText("Voice Input").performClick()

        // Button should now say "Stop"
        composeTestRule.onNodeWithText("Stop").assertExists()

        // Click to stop voice capture
        composeTestRule.onNodeWithText("Stop").performClick()

        // Button should revert to "Voice Input"
        composeTestRule.onNodeWithText("Voice Input").assertExists()
        
        // Since we didn't feed real audio, voicePreviewNotes is empty,
        // so "Commit" and "Discard" won't appear. We verify they don't exist.
        composeTestRule.onNodeWithText("Commit").assertDoesNotExist()
        composeTestRule.onNodeWithText("Discard").assertDoesNotExist()
    }
}
