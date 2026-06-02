package com.codexapp.ui.message

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

internal fun renderInlineMarkdown(text: String): AnnotatedString {
    val source = text.trimEnd()
    if (source.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        var index = 0
        while (index < source.length) {
            val codeStart = source.indexOf('`', index)
            val boldStart = source.indexOf("**", index)
            val italicStart = source.indexOf('*', index)
            val linkStart = source.indexOf("[", index)
            val next = listOf(codeStart, boldStart, italicStart, linkStart)
                .filter { it >= 0 }
                .minOrNull() ?: source.length
            if (next > index) {
                append(source.substring(index, next))
            }

            when {
                next == codeStart && codeStart >= 0 -> {
                    val end = source.indexOf('`', codeStart + 1)
                    if (end > codeStart + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x1A111827),
                                color = Color(0xFF111827)
                            )
                        ) {
                            append(source.substring(codeStart + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(source.substring(codeStart))
                        break
                    }
                }

                next == boldStart && boldStart >= 0 -> {
                    val end = source.indexOf("**", boldStart + 2)
                    if (end > boldStart + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(source.substring(boldStart + 2, end))
                        }
                        index = end + 2
                    } else {
                        append(source.substring(boldStart))
                        break
                    }
                }

                next == italicStart && italicStart >= 0 -> {
                    val end = source.indexOf('*', italicStart + 1)
                    if (end > italicStart + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(source.substring(italicStart + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(source.substring(italicStart))
                        break
                    }
                }

                next == linkStart && linkStart >= 0 -> {
                    val closeBracket = source.indexOf(']', linkStart + 1)
                    val openParen = if (closeBracket > linkStart) source.indexOf('(', closeBracket + 1) else -1
                    val closeParen = if (openParen > closeBracket) source.indexOf(')', openParen + 1) else -1
                    if (closeBracket > linkStart + 1 && openParen == closeBracket + 1 && closeParen > openParen + 1) {
                        val label = source.substring(linkStart + 1, closeBracket)
                        val url = source.substring(openParen + 1, closeParen)
                        withLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(
                                    style = SpanStyle(
                                        color = Color(0xFF2563EB),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            )
                        ) {
                            append(label)
                        }
                        index = closeParen + 1
                    } else {
                        append(source.substring(linkStart))
                        break
                    }
                }

                else -> break
            }
        }
    }
}

@Composable
internal fun MarkdownInlineText(
    text: String,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier
) {
    val annotatedText = remember(text) { renderInlineMarkdown(text) }
    Text(
        text = annotatedText,
        color = textColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier
    )
}
