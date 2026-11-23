// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.commercialkiller.data.audio.AudioWorkbenchEngine
import com.example.commercialkiller.data.model.Stage2Mode
import com.example.commercialkiller.theme.CommercialKillerTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
  private val engine by lazy { AudioWorkbenchEngine(applicationContext) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      CommercialKillerTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(engine = engine)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) {
      engine.close()
    }
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    if (intent.hasExtra("auto_mute")) {
      engine.setAutoMuteEnabled(intent.getBooleanExtra("auto_mute", true))
    }
    if (intent.hasExtra("simulate_muted_break")) {
      engine.setSimulateMutedBreak(intent.getBooleanExtra("simulate_muted_break", false))
    }
    if (intent.hasExtra("stage1_neural")) {
      engine.setUseNeuralAudioClassifier(intent.getBooleanExtra("stage1_neural", true))
    }
    if (intent.hasExtra("stage1_video")) {
      engine.setVideoFusionEnabled(intent.getBooleanExtra("stage1_video", true))
    }
    intent.getStringExtra("stage2_mode")?.let { modeStr ->
      val parsed = try {
        Stage2Mode.valueOf(modeStr.trim().uppercase(Locale.US))
      } catch (ignored: Exception) {
        null
      }
      if (parsed != null) {
        engine.setStage2Mode(parsed)
      }
    }

    val filePath = intent.getStringExtra("audio_file") ?: intent.getStringExtra("file")
    val uri = intent.data ?: (filePath?.let {
      if (it.startsWith("content://") || it.startsWith("file://")) {
        Uri.parse(it)
      } else {
        Uri.fromFile(File(it))
      }
    })

    if (uri != null) {
      engine.prepareForExternalFileIntent()
      lifecycleScope.launch {
        engine.loadAudioFile(this@MainActivity, uri)
      }
    }
  }
}
