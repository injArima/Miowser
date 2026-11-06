package com.miowser.app.browser

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementPickerScriptTest {
    private val palette = ElementPickerPalette.from(lightColorScheme())

    @Test
    fun `picker token is encoded as a javascript string`() {
        val script = elementPickerStartScript("token\"with\\characters", palette)

        assertTrue(script.contains("var token = \"token\\\"with\\\\characters\";"))
        assertFalse(script.contains("var token = token\"with"))
    }

    @Test
    fun `first element tap outlines the candidate and opens an action toolbar`() {
        val script = elementPickerStartScript("token", palette)

        assertTrue(script.contains("choose(0);"))
        assertTrue(script.contains("toolbar.style.display = 'block'"))
        assertTrue(script.indexOf("choose(0);") <
            script.indexOf("window.MiowserElementPicker.selected"))
        assertTrue(script.contains("rect.top >= 64"))
        assertTrue(script.contains("window.MiowserElementPicker.action(token, chosenIndex, operation)"))
    }

    @Test
    fun `picker colors come from the supplied app palette`() {
        val script = elementPickerStartScript("token", palette.copy(
            surface = Color(0xFF123456), accent = Color(0xFF789ABC)
        ))

        assertTrue(script.contains("background:#123456"))
        assertTrue(script.contains("border:3px solid #789ABC"))
        assertFalse(script.contains("background:#252129"))
    }

    @Test
    fun `commit action is quoted rather than interpolated as code`() {
        val script = elementPickerCommitScript("DELETE');alert(1)//")

        assertTrue(script.contains("commit(\"DELETE');alert(1)//\")"))
    }
}
