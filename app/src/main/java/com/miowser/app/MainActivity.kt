package com.miowser.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.miowser.app.browser.BrowserScreen
import com.miowser.app.ui.theme.MiowserTheme

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * App entry point. Edge-to-edge and the Compose theme are wired here from day one
 * (see project memory: retro flagged screen-by-screen inset handling as a real bug source).
 */
class MainActivity : ComponentActivity() {

    private val requestLocalNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Debug-build-only permission (see app/src/debug/AndroidManifest.xml) for reaching a
        // local dev/test server; requesting it is a harmless no-op in release builds since the
        // permission isn't declared there at all. Real Miowser browsing never needs this.
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocalNetworkPermission.launch(LOCAL_NETWORK_PERMISSION)
        }

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
