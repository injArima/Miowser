package com.miowser.app.browser.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAssistantButtonScriptTest {
    @Test
    fun `assistant requires active audible playback`() {
        assertTrue(VIDEO_ASSISTANT_BUTTON_SCRIPT.contains("video.paused || video.ended"))
        assertTrue(VIDEO_ASSISTANT_BUTTON_SCRIPT.contains("video.muted || !(video.volume > 0)"))
    }

    @Test
    fun `deprecated frame-local presentation is absent`() {
        assertFalse(VIDEO_ASSISTANT_BUTTON_SCRIPT.contains("frameConstrained"))
        assertTrue(videoAssistantButtonEnabledForFrame(enabled = true, mainFrame = true))
        assertFalse(videoAssistantButtonEnabledForFrame(enabled = true, mainFrame = false))
    }
}
