package com.miowser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.miowser.app.browser.BrowserScreen
import com.miowser.app.ui.theme.MiowserTheme

/**
 * App entry point. Edge-to-edge and the Compose theme are wired here from day one
 * (see project memory: retro flagged screen-by-screen inset handling as a real bug source).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiowserApp()
        }
    }
}

@Composable
private fun MiowserApp() {
    MiowserTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BrowserScreen()
        }
    }
}
