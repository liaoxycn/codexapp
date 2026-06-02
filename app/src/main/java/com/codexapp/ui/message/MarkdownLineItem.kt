package com.codexapp.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MarkdownLineItem(
    line: MarkdownLine,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    wrapContent: Boolean = false
) {
    when (line.kind) {
        MarkdownLineKind.EMPTY -> Spacer(Modifier.height(4.dp))
        MarkdownLineKind.HEADING -> MarkdownInlineText(
            text = line.text,
            textColor = textColor,
            fontSize = (fontSize.value + 2f).sp,
            lineHeight = (lineHeight.value + 2f).sp,
            modifier = Modifier
                .then(if (wrapContent) Modifier else Modifier.fillMaxWidth())
                .padding(top = 2.dp, bottom = 1.dp)
        )

        MarkdownLineKind.LIST_ITEM -> Row(
            modifier = if (wrapContent) Modifier else Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "•",
                color = textColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                modifier = Modifier.width(12.dp)
            )
            MarkdownInlineText(
                text = line.text,
                textColor = textColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                modifier = if (wrapContent) Modifier else Modifier.weight(1f)
            )
        }

        MarkdownLineKind.QUOTE -> Row(
            modifier = (if (wrapContent) Modifier else Modifier.fillMaxWidth())
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Spacer(
                modifier = Modifier
                    .width(2.dp)
                    .height(lineHeight.value.dp)
                    .padding(top = 2.dp)
                    .background(textColor.copy(alpha = 0.35f))
            )
            Spacer(Modifier.width(8.dp))
            MarkdownInlineText(
                text = line.text,
                textColor = textColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                modifier = if (wrapContent) Modifier else Modifier.weight(1f)
            )
        }

        MarkdownLineKind.CODE -> Text(
            text = line.text,
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = FontFamily.Monospace,
            modifier = (if (wrapContent) Modifier else Modifier.fillMaxWidth())
                .clip(RoundedCornerShape(6.dp))
                .background(textColor.copy(alpha = 0.06f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )

        MarkdownLineKind.PARAGRAPH -> MarkdownInlineText(
            text = line.text,
            textColor = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = Modifier
                .then(if (wrapContent) Modifier else Modifier.fillMaxWidth())
                .padding(bottom = 1.dp)
        )
    }
}
