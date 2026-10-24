package com.miowser.app.startup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.miowser.app.browser.*
import com.miowser.app.downloader.DownloadQueue
import com.miowser.app.library.LibraryFolders
import com.miowser.app.library.MediaLibrary
import com.miowser.app.settings.AppSettings
import kotlinx.coroutines.*
import kotlin.coroutines.resume

sealed interface StartupState {
    data object Loading : StartupState
    data class Ready(val settings: HubSettings) : StartupState
    data object Error : StartupState
}

object AppStartup {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hub = StartupTask<HubSettings>()
    private val privacy = StartupTask<Unit>()
    private var job: Job? = null
    var state by mutableStateOf<StartupState>(StartupState.Loading)
        private set

    fun updateHubSettings(settings: HubSettings) {
        com.miowser.app.persistence.Persistence.requireOwner()
        if (state is StartupState.Ready) state = StartupState.Ready(settings)
    }

    fun start(context: Context) {
        if (job?.isActive == true || state is StartupState.Ready) return
        val app = context.applicationContext
        state = StartupState.Loading
        job = scope.launch {
            try {
                val settings = hub.get {
                    AppSettings.loadAsync(app)
                    val saved = withContext(Dispatchers.IO) {
                        com.miowser.app.library.LocalInspectionCache.prepare(app)
                        listOf("player_track_preferences", "player_subtitle_style", "player_display_preferences",
                            "site_permissions", "backup_settings").forEach {
                            app.getSharedPreferences(it, Context.MODE_PRIVATE).all
                        }
                        HubSettingsStore.load(app)
                    }
                    LibraryFolders.restoreAsync(app)
                    MediaLibrary.restoreAsync(app)
                    DownloadQueue.restoreAsync(app)
                    saved
                }
                state = StartupState.Ready(settings)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state = StartupState.Error }
        }
    }

    /** Shared barrier for browser entry, remote playback and cookie-dependent transfers. */
    suspend fun ensurePrivacy(context: Context) {
        val app = context.applicationContext
        withContext(Dispatchers.Main.immediate) {
            // CookieManager and private-session cleanup can implicitly start WebView too.
            WebViewStartup.ensureReady(app)
            privacy.get {
                if (withContext(Dispatchers.IO) { PrivateSessionStore.wasInterrupted(app) }) {
                    suspendCancellableCoroutine { continuation ->
                        BrowsingData.clearSharedPrivateSessionData(app, emptyList()) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    withContext(Dispatchers.IO) { check(PrivateSessionStore.setActive(app, false)) }
                }
            }
        }
    }
}
