package com.codexapp.ui.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.codexapp.model.ConnectionStatus
import com.codexapp.ui.theme.CodexTheme

@Composable
internal fun RowScope.ComposerInputField(
    value: TextFieldValue,
    compactMode: Boolean,
    composerEnabled: Boolean,
    connectionStatus: ConnectionStatus,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    onFocusComposer: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onSendNow: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable(
                enabled = composerEnabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                onFocusComposer()
            }
            .padding(horizontal = 12.dp, vertical = 0.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = 1,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { onSendNow() },
                onDone = { onSendNow() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("composer_input_field"),
            enabled = composerEnabled,
            textStyle = composerInputTextStyle(
                compactMode = compactMode,
                composerEnabled = composerEnabled
            ),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            composerPlaceholder(composerEnabled, connectionStatus),
                            color = if (composerEnabled) CodexTheme.colors.textSecondary else CodexTheme.colors.textTertiary,
                            fontSize = ComposerTextSize,
                            lineHeight = ComposerTextLineHeight
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
internal fun composerInputTextStyle(
    compactMode: Boolean,
    composerEnabled: Boolean
): TextStyle {
    return TextStyle(
        color = if (composerEnabled) CodexTheme.colors.textPrimary else CodexTheme.colors.textTertiary,
        fontSize = ComposerTextSize,
        lineHeight = ComposerTextLineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
}
