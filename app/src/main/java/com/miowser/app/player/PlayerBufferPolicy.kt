package com.miowser.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal enum class PlayerConnection { WIFI, MOBILE, OFFLINE }

internal data class PlayerBufferWindow(val aheadMs: Long, val behindMs: Long)

private const val MIB = 1024L * 1024

/** Encoded sample memory, separate from the larger on-disk prefetch window below. */
internal fun playerSampleBufferBytes(maxHeapBytes: Long, lowRam: Boolean, availableBytes: Long): Int {
    val ceiling = if (lowRam || maxHeapBytes <= 256 * MIB) 16 * MIB else 32 * MIB
    val usual = (maxHeapBytes / 12).coerceIn(8 * MIB, ceiling)
    val reserve = (maxHeapBytes / 8).coerceIn(12 * MIB, 32 * MIB)
    // Leave room for decoders, the UI and installed filters; don't consume all remaining heap.
    return minOf(usual, ((availableBytes - reserve).coerceAtLeast(0) / 2))
        .coerceAtLeast(4 * MIB).toInt()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun playerLoadControl(context: Context): androidx.media3.exoplayer.DefaultLoadControl {
    val runtime = Runtime.getRuntime()
    val lowRam = context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true
    return playerLoadControl(playerSampleBufferBytes(runtime.maxMemory(), lowRam,
        runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())))
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun playerLoadControl(targetBytes: Int): androidx.media3.exoplayer.DefaultLoadControl =
    androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(30_000, 90_000, 2_500, 5_000)
        .setTargetBufferBytes(targetBytes)
        .setPrioritizeTimeOverSizeThresholds(false)
        // Past samples otherwise consume the same allocator as forward samples and can prevent
        // the loader making progress. Seeking still uses the disk cache or the source file.
        .setBackBuffer(0, false)
        .build()

internal fun playerBufferWindow(connection: PlayerConnection): PlayerBufferWindow = when (connection) {
    PlayerConnection.WIFI -> PlayerBufferWindow(aheadMs = 15 * 60_000L, behindMs = 5 * 60_000L)
    PlayerConnection.MOBILE -> PlayerBufferWindow(aheadMs = 5 * 60_000L, behindMs = 5 * 60_000L)
    PlayerConnection.OFFLINE -> PlayerBufferWindow(aheadMs = 0L, behindMs = 5 * 60_000L)
}

/** Read on each prefetch pass so Wi-Fi/cellular handovers change the budget without restarting playback. */
internal fun playerConnection(context: Context): PlayerConnection {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return PlayerConnection.OFFLINE
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        ?: return PlayerConnection.OFFLINE
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return PlayerConnection.OFFLINE
    }
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> PlayerConnection.WIFI
        else -> PlayerConnection.MOBILE // Cellular, metered VPN, and unknown transports use the smaller window.
    }
}
