package com.miowser.app.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementPickerScriptTest {
    @Test
    fun `picker token is encoded as a javascript string`() {
        val script = elementPickerStartScript("token\"with\\characters")

        assertTrue(script.contains("var token = \"token\\\"with\\\\characters\";"))
        assertFalse(script.contains("var token = token\"with"))
    }

    @Test
    fun `first element tap outlines the candidate and opens an action toolbar`() {
        val script = elementPickerStartScript("token")

        assertTrue(script.contains("choose(0);"))
        assertTrue(script.contains("toolbar.style.display = 'block'"))
        assertTrue(script.indexOf("choose(0);") <
            script.indexOf("window.MiowserElementPicker.selected"))
        assertTrue(script.contains("rect.top >= 58"))
        assertTrue(script.contains("window.MiowserElementPicker.action(token, chosenIndex, operation)"))
    }

    @Test
    fun `commit action is quoted rather than interpolated as code`() {
        val script = elementPickerCommitScript("DELETE');alert(1)//")

        assertTrue(script.contains("commit(\"DELETE');alert(1)//\")"))
    }
}
