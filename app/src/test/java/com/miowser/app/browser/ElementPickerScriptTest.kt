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
    fun `preview index is constrained to available candidate range`() {
        assertTrue(elementPickerPreviewScript(-10).contains("preview(0)"))
        assertTrue(elementPickerPreviewScript(100).contains("preview(5)"))
    }

    @Test
    fun `commit action is quoted rather than interpolated as code`() {
        val script = elementPickerCommitScript("DELETE');alert(1)//")

        assertTrue(script.contains("commit(\"DELETE');alert(1)//\")"))
    }
}
