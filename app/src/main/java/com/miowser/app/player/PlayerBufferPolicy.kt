package com.miowser.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal enum class PlayerConnection { WIFI, MOBILE, OFFLINE }

internal data class PlayerBufferWindow(val aheadMs: Long, val behindMs: Long)

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
