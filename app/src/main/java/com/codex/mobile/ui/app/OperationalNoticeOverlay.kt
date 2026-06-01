package com.codex.mobile.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.model.OperationalNotice
import kotlinx.coroutines.delay

@Composable
internal fun OperationalNoticeOverlay(
    notices: List<OperationalNotice>,
    modifier: Modifier = Modifier
) {
    val visibleNotices = remember { mutableStateListOf<OperationalNotice>() }
    val seenKeys = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(notices) {
        notices.forEach { notice ->
            val key = "${notice.id}:${notice.createdAt}:${notice.text}"
            if (seenKeys.put(key, true) != true) {
                visibleNotices.add(0, notice)
                while (visibleNotices.size > 5) {
                    visibleNotices.removeAt(visibleNotices.lastIndex)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 88.dp, end = 12.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            visibleNotices.forEach { notice ->
                OperationalNoticeItem(
                    notice = notice,
                    onExpired = { visibleNotices.remove(notice) }
                )
            }
        }
    }
}

@Composable
private fun OperationalNoticeItem(
    notice: OperationalNotice,
    onExpired: () -> Unit
) {
    var visible by remember(notice.id, notice.createdAt, notice.text) { mutableStateOf(false) }
    LaunchedEffect(notice.id, notice.createdAt, notice.text) {
        visible = true
        delay(2600L)
        visible = false
        delay(650L)
        onExpired()
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)) + slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth / 3 },
            animationSpec = tween(220)
        ),
        exit = fadeOut(animationSpec = tween(650)) + slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth / 5 },
            animationSpec = tween(650)
        )
    ) {
        val noticeShape = RoundedCornerShape(12.dp)
        Text(
            text = notice.text.lineSequence().firstOrNull().orEmpty(),
            modifier = Modifier
                .testTag("operational_notice_${notice.id}")
                .widthIn(max = 280.dp)
                .alpha(0.96f)
                .background(
                    color = Color(0xCCFFFFFF),
                    shape = noticeShape
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
            color = Color(0xCC111111),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
