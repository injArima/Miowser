package com.miowser.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.miowser.app.ui.theme.BrandTypography
import org.junit.Assert.*
import org.junit.Test

class BrandTextTest {
    @Test fun onlyTheAppNameReceivesTheBrandFont() {
        val text = "Welcome to Miowser — play videos"
        val branded = withBrandNameFont(AnnotatedString(text))
        assertEquals(text, branded.text)
        val span = branded.spanStyles.single()
        assertEquals("Miowser", text.substring(span.start, span.end))
        assertEquals(BrandTypography.fontFamily, span.item.fontFamily)
        assertEquals(FontWeight.Normal, span.item.fontWeight)
    }

    @Test fun existingTextAnnotationsAndOtherStylingArePreserved() {
        val original = buildAnnotatedString {
            append("Open Miowser settings")
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, 4)
            addStringAnnotation("action", "settings", 13, 21)
        }
        val branded = withBrandNameFont(original)
        assertEquals(original.spanStyles.first(), branded.spanStyles.first())
        assertEquals(original.getStringAnnotations(0, original.length),
            branded.getStringAnnotations(0, branded.length))
    }

    @Test fun normalLabelsAndNamesContainingTheBrandRemainUnchanged() {
        val original = AnnotatedString("Playback speed and MyMiowserBackup")
        assertSame(original, withBrandNameFont(original))
        assertTrue(brandNameRanges("miowser_backup MyMiowser").isEmpty())
    }

    @Test fun brandInSentencesPathsAndPossessivesUsesTheSameFont() {
        val text = "Miowser's folder: Downloads/Miowser. MIOWSER"
        assertEquals(listOf("Miowser", "Miowser", "MIOWSER"),
            brandNameRanges(text).map { text.substring(it) })
    }
}
