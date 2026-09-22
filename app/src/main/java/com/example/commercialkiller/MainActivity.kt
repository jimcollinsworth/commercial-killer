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
import com.example.commercialkiller.theme.CommercialKillerTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
  private val engine = AudioWorkbenchEngine()

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

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    val filePath = intent.getStringExtra("audio_file") ?: intent.getStringExtra("file")
    val uri = intent.data ?: (filePath?.let {
      if (it.startsWith("content://") || it.startsWith("file://")) {
        Uri.parse(it)
      } else {
        Uri.fromFile(File(it))
      }
    })

    if (uri != null) {
      lifecycleScope.launch {
        engine.loadAudioFile(this@MainActivity, uri)
      }
    }
  }
}
