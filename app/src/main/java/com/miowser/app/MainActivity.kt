package com.miowser.app

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.app.PictureInPictureParams
import android.util.Rational
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.miowser.app.persistence.PersistenceNotice
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.miowser.app.ui.theme.MiowserTheme
import com.miowser.app.ui.theme.AppThemeMode
import com.miowser.app.ui.theme.AppThemePreference

/**
 * App entry point. Edge-to-edge and the Compose theme are wired here from day one
 * (see project memory: retro flagged screen-by-screen inset handling as a real bug source).
 */
class MainActivity : ComponentActivity() {
    internal var browserVideoPipOwner by mutableStateOf<com.miowser.app.browser.video.BrowserVideoPipOwner?>(null)
        private set
    private var shortcutSequence = 0L
    private var shortcutRequest by mutableStateOf<LauncherShortcutRequest?>(null)
    internal var playerInPictureInPicture by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherShortcuts.retireLeaks(this)
        receiveShortcut(intent)
        enableEdgeToEdge()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().penaltyLog().build())
        }
        if (BuildConfig.BUILD_TYPE == "development") {
            android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.Builder()
                .detectActivityLeaks().detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects().penaltyLog().build())
        }

        AppThemePreference.load(this)

        setContent {
            MiowserApp(shortcutRequest) {
                shortcutRequest = null
                // A later activity recreation must not repeat an already handled shortcut.
                setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShortcut(intent)
    }

    internal fun enterPlayerPictureInPicture(width: Int = 16, height: Int = 9): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            val aspect = (width.toFloat() / height.coerceAtLeast(1)).coerceIn(0.5f, 2f)
            val ratio = Rational((aspect * 1000).toInt(), 1000)
            return enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
        }
        return false
    }

    internal fun enterBrowserVideoPictureInPicture(owner: com.miowser.app.browser.video.BrowserVideoPipOwner,
        width: Int, height: Int): Boolean {
        browserVideoPipOwner = owner // Set before Android's onPause callback during entry.
        val entered = runCatching { enterPlayerPictureInPicture(width, height) }.getOrDefault(false)
        if (!entered) browserVideoPipOwner = null
        return entered
    }

    internal fun releaseBrowserVideoPictureInPicture(owner: com.miowser.app.browser.video.BrowserVideoPipOwner) {
        if (browserVideoPipOwner == owner) browserVideoPipOwner = null
    }

    override fun onResume() {
        super.onResume()
        if (!isInPictureInPictureMode) browserVideoPipOwner = null
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean,
        newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerInPictureInPicture = isInPictureInPictureMode
    }

    private fun receiveShortcut(intent: Intent?) {
        val action = intent?.action
        if (LauncherShortcuts.isSupported(action)) {
            shortcutRequest = LauncherShortcutRequest(++shortcutSequence, requireNotNull(action))
        } else if (action == Intent.ACTION_VIEW) {
            val url = intent.data?.takeIf { uri ->
                (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
                    !uri.host.isNullOrBlank()
            }?.toString() ?: return
            shortcutRequest = LauncherShortcutRequest(++shortcutSequence, Intent.ACTION_VIEW, url)
        }
    }
}

@Composable
private fun MiowserApp(
    shortcutRequest: LauncherShortcutRequest?,
    onShortcutHandled: () -> Unit
) {
    val activity = LocalContext.current as? ComponentActivity
    val systemDark = isSystemInDarkTheme()
    val dark = when (AppThemePreference.mode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.AMOLED -> true
    }
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MiowserTheme(darkTheme = dark, amoled = AppThemePreference.mode == AppThemeMode.AMOLED) {
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                com.miowser.app.startup.StartupHost(shortcutRequest, onShortcutHandled)
                PersistenceNotice(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))
            }
        }
    }
}
