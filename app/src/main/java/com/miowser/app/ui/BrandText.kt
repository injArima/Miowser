package com.miowser.app.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.res.ResourcesCompat
import com.miowser.app.ui.theme.BrandTypography

private val brandPattern = Regex("(?i)(?<![\\p{L}\\p{N}_])${BrandTypography.name}(?![\\p{L}\\p{N}_])")

internal fun brandNameRanges(text: CharSequence): List<IntRange> =
    brandPattern.findAll(text).map { it.range }.toList()

internal fun withBrandNameFont(text: AnnotatedString): AnnotatedString {
    val ranges = brandNameRanges(text)
    if (ranges.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        ranges.forEach { range -> addStyle(SpanStyle(fontFamily = BrandTypography.fontFamily,
            fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal, fontSynthesis = FontSynthesis.None),
            range.first, range.last + 1) }
    }
}

/** Drop-in Material text with a zero-allocation fast path for labels without the app name. */
@Composable
internal fun AppText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current
) {
    if (text.contains(BrandTypography.name, ignoreCase = true)) {
        AppText(remember(text) { AnnotatedString(text) }, modifier, color, fontSize,
            fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign, lineHeight,
            overflow, softWrap, maxLines, minLines, onTextLayout = onTextLayout, style = style)
    } else {
        androidx.compose.material3.Text(text = text, modifier = modifier, color = color,
            fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
            letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
            minLines = minLines, onTextLayout = onTextLayout, style = style)
    }
}

@Composable
internal fun AppText(
    text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current
) {
    val branded = if (text.text.contains(BrandTypography.name, ignoreCase = true)) {
        remember(text) { withBrandNameFont(text) }
    } else text
    androidx.compose.material3.Text(text = branded, modifier = modifier, color = color,
        fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, inlineContent = inlineContent, onTextLayout = onTextLayout ?: {}, style = style)
}

/** ActionMode labels use Android spans rather than Compose text. */
internal fun brandNameNativeText(context: Context, text: String): CharSequence {
    val ranges = brandNameRanges(text)
    if (ranges.isEmpty()) return text
    val typeface = ResourcesCompat.getFont(context, BrandTypography.fontResource) ?: return text
    return SpannableString(text).apply {
        ranges.forEach { setSpan(BrandTypefaceSpan(typeface), it.first, it.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
}

private class BrandTypefaceSpan(private val brandTypeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = apply(paint)
    override fun updateMeasureState(paint: TextPaint) = apply(paint)
    private fun apply(paint: TextPaint) {
        paint.typeface = brandTypeface
        paint.isFakeBoldText = false
        paint.textSkewX = 0f
    }
}
