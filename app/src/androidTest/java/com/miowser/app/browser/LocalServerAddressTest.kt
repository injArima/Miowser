package com.miowser.app.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalServerAddressTest {
    @Test fun developmentPermissionTargetsLocalServersOnly() {
        listOf("http://localhost:8080", "http://10.0.2.2:8080",
            "https://192.168.1.1", "http://device.local", "http://[::1]:3000")
            .forEach { assertTrue(it, isLocalServerAddress(it)) }
        listOf("https://example.com", "https://127.0.0.1.example.com", "about:blank")
            .forEach { assertFalse(it, isLocalServerAddress(it)) }
    }
}
