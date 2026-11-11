package com.miowser.app.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserPageGesturePolicyTest {
    @Test fun `normal top page needs a target answer and a clear downward drag`() {
        val policy = BrowserPageGesturePolicy(8f)
        policy.begin(true)
        policy.move(1f, 50f, 1)
        assertFalse(policy.canRefresh)
        policy.resolvePageTarget(true)
        assertTrue(policy.canRefresh)
    }

    @Test fun `controls and nested scrollers keep the whole gesture`() {
        val policy = BrowserPageGesturePolicy(8f)
        policy.begin(true)
        policy.resolvePageTarget(false)
        policy.move(0f, 200f, 1)
        assertFalse(policy.canRefresh)
        policy.resolvePageTarget(true)
        assertFalse(policy.canRefresh)
    }

    @Test fun `a horizontal swipe cannot turn into refresh when it curves down`() {
        val policy = BrowserPageGesturePolicy(8f)
        policy.begin(true)
        policy.resolvePageTarget(true)
        policy.move(30f, 10f, 1)
        policy.move(30f, 200f, 1)
        assertFalse(policy.canRefresh)
    }

    @Test fun `pinch and child interception requests remain page owned`() {
        val policy = BrowserPageGesturePolicy(8f)
        policy.begin(true); policy.resolvePageTarget(true)
        policy.move(0f, 100f, 2); policy.move(0f, 200f, 1)
        assertFalse(policy.canRefresh)
        policy.begin(true); policy.resolvePageTarget(true)
        policy.claimForPage(); policy.move(0f, 100f, 1)
        assertFalse(policy.canRefresh)
    }

    @Test fun `upward and mid page gestures never become a pull at the page top`() {
        val policy = BrowserPageGesturePolicy(8f)
        policy.begin(false); policy.resolvePageTarget(true); policy.move(0f, 150f, 1)
        assertFalse(policy.canRefresh)
        policy.begin(true); policy.resolvePageTarget(true)
        policy.move(0f, -30f, 1); policy.move(0f, 150f, 1)
        assertFalse(policy.canRefresh)
    }

    @Test fun `small or diagonal movement and cancelled pulls do not refresh`() {
        val policy = BrowserPageGesturePolicy(8f)
        policy.begin(true); policy.resolvePageTarget(true)
        policy.move(0f, 10f, 1); assertFalse(policy.canRefresh)
        policy.move(15f, 20f, 1); assertFalse(policy.canRefresh)
        policy.move(15f, 100f, 1); assertTrue(policy.canRefresh)
        policy.cancel(); assertFalse(policy.canRefresh)
        policy.resolvePageTarget(true); assertFalse(policy.canRefresh)
    }
}
