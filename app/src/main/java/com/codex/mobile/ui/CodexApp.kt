package com.codex.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalDrawer
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.SystemClock
import com.codex.mobile.model.ComposerChip
import com.codex.mobile.model.ComposerChipIcon
import com.codex.mobile.model.ConnectionStatus
import com.codex.mobile.model.GatewayConfig
import com.codex.mobile.model.HomeUiState
import com.codex.mobile.model.MessageBlock
import com.codex.mobile.model.MessageRole
import com.codex.mobile.model.ThreadMessage
import com.codex.mobile.model.ThreadStatus
import com.codex.mobile.model.ThreadSummary
import com.codex.mobile.ui.theme.CodexTheme
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun CodexApp(
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showGatewayDialog by rememberSaveable { mutableStateOf(false) }
    var compactMode by rememberSaveable { mutableStateOf(false) }
    var composerPanel by rememberSaveable { mutableStateOf("none") }
    var lastBackPressAt by rememberSaveable { mutableStateOf(0L) }

    BackHandler(enabled = true) {
        if (showGatewayDialog) {
            showGatewayDialog = false
            return@BackHandler
        }
        if (composerPanel != "none") {
            composerPanel = "none"
            return@BackHandler
        }
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
            return@BackHandler
        }
        if (state.showComposerDetails) {
            viewModel.closeComposerDetails()
            return@BackHandler
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPressAt < 1800L) {
            android.os.Process.killProcess(android.os.Process.myPid())
        } else {
            lastBackPressAt = now
        }
    }

    if (showGatewayDialog) {
        GatewayDialog(
            config = state.gatewayConfig,
            isConnected = state.connectionStatus == ConnectionStatus.CONNECTED,
            onDismiss = { showGatewayDialog = false },
            onConnect = { url, pairToken ->
                viewModel.connect(url, pairToken)
                showGatewayDialog = false
            },
            onDisconnect = {
                viewModel.disconnect()
                showGatewayDialog = false
            }
        )
    }

    ModalDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                state = state,
                onCreateThread = {
                    viewModel.createThread()
                    scope.launch { drawerState.close() }
                },
                onRefreshThreads = viewModel::refreshThreads,
                onSelectThread = {
                    viewModel.selectThread(it)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(CodexTheme.colors.background),
            topBar = {
                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    val selectedThread = state.threads.firstOrNull { it.id == state.selectedThreadId }
                    TopBar(
                        title = selectedThread?.title ?: "Codex",
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onCreateThread = viewModel::createThread,
                        onOpenConnection = { showGatewayDialog = true }
                    )
                }
            },
            bottomBar = {
                Composer(
                    state = state,
                    compactMode = compactMode,
                    activePanel = composerPanel,
                    onActivePanelChange = { composerPanel = it },
                    onToggleCompact = { compactMode = !compactMode },
                    onToggleDetails = viewModel::toggleComposerDetails,
                    onChange = viewModel::updateComposer,
                    onInsertText = viewModel::insertComposerText,
                    onApplySlashCommand = viewModel::applySlashCommand,
                    onClearComposer = viewModel::clearComposer,
                    onSend = viewModel::send,
                    onStop = viewModel::stopGenerating
                )
            },
            backgroundColor = CodexTheme.colors.background
        ) { padding ->
            ThreadScreen(
                modifier = Modifier.padding(padding),
                state = state,
                compactMode = compactMode,
                onOpenConnection = { showGatewayDialog = true },
                onRefreshCurrent = viewModel::refreshCurrentThreadAnimated,
                onApprovePending = viewModel::approvePending,
                onRejectPending = viewModel::rejectPending
            )
        }
    }
}

@Composable
private fun RenameThreadDialog(
    value: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "重命名会话",
                color = CodexTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("会话名称") }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = value.trim().isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun GatewayDialog(
    config: GatewayConfig,
    isConnected: Boolean,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var url by remember(config.url) { mutableStateOf(config.url) }
    var pairToken by remember(config.pairToken) { mutableStateOf(config.pairToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "连接 desktop gateway",
                color = CodexTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("WebSocket 地址") },
                    placeholder = { Text("ws://10.0.2.2:8765/mobile") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = pairToken,
                    onValueChange = { pairToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("配对码 / token") },
                    placeholder = { Text("可选") },
                    singleLine = true
                )
                Text(
                    text = "Android 只连 desktop gateway；账号、key、mcp、skill 都由桌面端处理。",
                    color = CodexTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(url, pairToken) },
                enabled = url.isNotBlank()
            ) {
                Text("连接")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isConnected) {
                    TextButton(onClick = onDisconnect) {
                        Text("断开")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun TopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    onCreateThread: () -> Unit,
    onOpenConnection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodexTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderIconButton(
            icon = Icons.Filled.Menu,
            contentDescription = "打开抽屉",
            onClick = onOpenDrawer
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = CodexTheme.colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HeaderIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "新建会话",
            onClick = onCreateThread
        )
        HeaderIconButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "连接设置",
            onClick = onOpenConnection
        )
    }
    Divider(color = CodexTheme.colors.border)
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CodexTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DrawerContent(
    state: HomeUiState,
    onCreateThread: () -> Unit,
    onRefreshThreads: () -> Unit,
    onSelectThread: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val matches: (ThreadSummary) -> Boolean = { thread ->
        normalizedQuery.isBlank() ||
            thread.title.contains(normalizedQuery, ignoreCase = true) ||
            thread.preview.contains(normalizedQuery, ignoreCase = true)
    }
    val activeThreads = state.threads.filter(matches)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .width(304.dp)
            .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .background(CodexTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "会话",
                    color = CodexTheme.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ConnectionStatusLine(
                    status = state.connectionStatus,
                    detail = state.connectionDetail
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DrawerHeaderAction(
                    icon = Icons.Filled.Add,
                    contentDescription = "新建会话",
                    onClick = onCreateThread
                )
                DrawerHeaderAction(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "刷新会话",
                    onClick = onRefreshThreads
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        DrawerSearchBar(
            query = query,
            onChange = { query = it }
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (activeThreads.isEmpty()) {
                item {
                    Text(
                        text = "没有匹配的会话",
                        color = CodexTheme.colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
            items(activeThreads) { thread ->
                ThreadRow(
                    summary = thread,
                    selected = thread.id == state.selectedThreadId,
                    showGenerating = state.selectedThreadId == thread.id && state.isGenerating,
                    onClick = { onSelectThread(thread.id) }
                )
            }
        }
        }
    }
}

@Composable
private fun ConnectionStatusLine(
    status: ConnectionStatus,
    detail: String
) {
    val statusText = when (status) {
        ConnectionStatus.CONNECTED -> "已连接 desktop gateway"
        ConnectionStatus.CONNECTING -> "正在连接 desktop gateway"
        ConnectionStatus.ERROR -> detail.ifBlank { "连接异常" }
        ConnectionStatus.DISCONNECTED -> "未连接 desktop gateway"
    }
    Text(
        text = statusText,
        color = when (status) {
            ConnectionStatus.CONNECTED -> Color(0xFF059669)
            ConnectionStatus.CONNECTING -> Color(0xFF2563EB)
            ConnectionStatus.ERROR -> Color(0xFFDC2626)
            ConnectionStatus.DISCONNECTED -> Color(0xFFF59E0B)
        },
        fontSize = 9.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DrawerHeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CodexTheme.colors.textPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun GroupHeader(
    label: String,
    compact: Boolean = false,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 1.dp else 2.dp)
            .clickable(enabled = onToggle != null) { onToggle?.invoke() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = if (compact) FontWeight.Normal else FontWeight.Medium
            )
        }
        if (expanded != null) {
            Text(
                text = if (expanded) "收起" else "展开",
                color = CodexTheme.colors.textTertiary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun DrawerSearchBar(
    query: String,
    onChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(13.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⌕", color = CodexTheme.colors.textTertiary, fontSize = 11.sp)
        Spacer(Modifier.width(5.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = CodexTheme.colors.textPrimary,
                fontSize = 11.sp
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (query.isBlank()) {
                    Text("搜索会话", color = CodexTheme.colors.textTertiary, fontSize = 11.sp)
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun ThreadRow(
    summary: ThreadSummary,
    selected: Boolean,
    showGenerating: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) CodexTheme.colors.surfaceSubtle else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(summary.status)
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.title,
                    modifier = Modifier.weight(1f),
                    color = CodexTheme.colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ThreadStatusText(
                    status = summary.status,
                    isGenerating = showGenerating,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Text(
                text = summary.preview,
                color = CodexTheme.colors.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ThreadStatusText(
    status: ThreadStatus,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val text = when {
        isGenerating || status == ThreadStatus.RUNNING -> "运行中"
        status == ThreadStatus.NEEDS_APPROVAL -> "待审批"
        status == ThreadStatus.FAILED -> "失败"
        else -> "空闲"
    }
    val color = when {
        isGenerating || status == ThreadStatus.RUNNING -> Color(0xFF2563EB)
        status == ThreadStatus.NEEDS_APPROVAL -> Color(0xFFF59E0B)
        status == ThreadStatus.FAILED -> Color(0xFFDC2626)
        else -> CodexTheme.colors.textTertiary
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 6.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StatusDot(status: ThreadStatus) {
    val color = when (status) {
        ThreadStatus.RUNNING -> Color(0xFF2563EB)
        ThreadStatus.IDLE -> Color(0xFF9CA3AF)
        ThreadStatus.NEEDS_APPROVAL -> Color(0xFFF59E0B)
        ThreadStatus.FAILED -> Color(0xFFDC2626)
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun ThreadScreen(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    compactMode: Boolean,
    onOpenConnection: () -> Unit,
    onRefreshCurrent: () -> Unit,
    onApprovePending: () -> Unit,
    onRejectPending: () -> Unit
    ) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val composerPadding = when {
        state.showComposerDetails && compactMode -> 278.dp
        state.showComposerDetails -> 298.dp
        compactMode -> 106.dp
        else -> 114.dp
    }
    val lastMessageRevision = state.messages.lastOrNull()?.revisionKey()
    val totalItems = 1 +
        (if (state.pendingApproval != null) 1 else 0) +
        (if (state.messages.isEmpty()) 1 else state.messages.size)
    val lastItemIndex = (totalItems - 1).coerceAtLeast(0)
    var pullDistance by rememberSaveable(state.selectedThreadId) { mutableFloatStateOf(0f) }
    var pullVelocity by rememberSaveable(state.selectedThreadId) { mutableFloatStateOf(0f) }
    var lastPullSampleAt by rememberSaveable(state.selectedThreadId) { mutableStateOf(0L) }
    var pullHintVisibleUntil by rememberSaveable(state.selectedThreadId) { mutableStateOf(0L) }
    val pullThreshold = remember(pullVelocity) {
        val speedBoost = (pullVelocity / 900f).coerceIn(0f, 0.35f)
        (160f * (1f - speedBoost)).coerceIn(110f, 160f)
    }
    val rawProgress = (pullDistance / pullThreshold).coerceIn(0f, 1f)
    val pullProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(stiffness = 420f),
        label = "pull-progress"
    )
    val showPullHint = pullDistance > 0f || state.isManualRefreshing || SystemClock.uptimeMillis() < pullHintVisibleUntil
    val showJumpToBottom by remember {
        derivedStateOf {
            val totalCount = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalCount > 0 && lastVisibleIndex < lastItemIndex
        }
    }
    val pullConnection = remember(state.selectedThreadId, state.isGenerating, state.isManualRefreshing, lastItemIndex) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (state.isGenerating || state.isManualRefreshing) {
                    return Offset.Zero
                }
                val atBottom = !listState.canScrollForward || listState.layoutInfo.totalItemsCount == 0
                if (!atBottom) {
                    if (pullDistance != 0f) {
                        pullDistance = 0f
                        pullVelocity = 0f
                    }
                    return Offset.Zero
                }
                if (available.y < 0f) {
                    val now = SystemClock.uptimeMillis()
                    val elapsed = (now - lastPullSampleAt).coerceAtLeast(1L).toFloat()
                    pullVelocity = ((-available.y) / elapsed) * 1000f
                    lastPullSampleAt = now
                    pullDistance = (pullDistance - available.y).coerceAtMost(260f)
                } else if (available.y > 0f && pullDistance > 0f) {
                    pullDistance = 0f
                    pullVelocity = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                val atBottom = !listState.canScrollForward || listState.layoutInfo.totalItemsCount == 0
                if (atBottom && !state.isGenerating && !state.isManualRefreshing && pullDistance >= pullThreshold) {
                    pullHintVisibleUntil = SystemClock.uptimeMillis() + 700L
                    onRefreshCurrent()
                }
                pullDistance = 0f
                pullVelocity = 0f
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(lastMessageRevision, state.pendingApproval, state.selectedThreadId, state.isGenerating) {
        if (state.messages.isNotEmpty()) {
            if (state.isGenerating) {
                listState.scrollToItem(lastItemIndex)
            } else {
                listState.animateScrollToItem(lastItemIndex)
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CodexTheme.colors.background)
            .nestedScroll(pullConnection)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 2.dp,
                bottom = composerPadding + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 5.dp)
        ) {
            if (state.hasMoreHistory) {
                item {
                    Spacer(Modifier.height(2.dp))
                }
            }

            if (state.messages.isEmpty()) {
                item {
                    if (state.isThreadSwitching) {
                        ThreadSwitchingCard(state.pendingThreadTitle)
                    } else {
                        EmptyThreadCard(
                            connected = state.connectionStatus == ConnectionStatus.CONNECTED,
                            hasThreads = state.threads.isNotEmpty()
                        )
                    }
                }
            }

            items(
                items = state.messages,
                key = { message -> message.id }
            ) { message ->
                MessageCard(message, compactMode)
            }

            if (state.pendingApproval != null) {
                item {
                    ApprovalCard(
                        text = state.pendingApproval,
                        onApprove = onApprovePending,
                        onReject = onRejectPending,
                        compactMode = compactMode
                    )
                }
            }
        }
        if (showPullHint) {
            PullRefreshHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = composerPadding + 8.dp)
                    .offset(y = (-((pullProgress * 12f).coerceAtMost(12f))).dp),
                refreshing = state.isManualRefreshing,
                generating = state.isGenerating,
                progress = pullProgress,
                compactMode = compactMode
            )
        }
        AnimatedVisibility(
            visible = showJumpToBottom,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp, bottom = composerPadding + 72.dp),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120))
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(CodexTheme.colors.surface)
                    .border(1.dp, CodexTheme.colors.border, CircleShape)
            ) {
                IconButton(
                    onClick = {
                        if (state.messages.isNotEmpty()) {
                            scope.launch {
                                listState.animateScrollToItem(lastItemIndex)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "滚到底部",
                        tint = CodexTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun PullRefreshHint(
    modifier: Modifier = Modifier,
    refreshing: Boolean,
    generating: Boolean,
    progress: Float,
    compactMode: Boolean,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        AnimatedVisibility(
            visible = progress > 0f || refreshing || generating,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(180))
        ) {
            Text(
                text = when {
                    refreshing || generating -> "刷新会话中"
                    progress >= 1f -> "松开刷新"
                    else -> "继续上滑"
                },
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(CodexTheme.colors.surface.copy(alpha = 0.92f))
                    .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ConnectionBanner(
    state: HomeUiState,
    compact: Boolean,
    onOpenConnection: () -> Unit
) {
    val statusColor = when (state.connectionStatus) {
        ConnectionStatus.CONNECTED -> Color(0xFF059669)
        ConnectionStatus.CONNECTING -> Color(0xFF2563EB)
        ConnectionStatus.ERROR -> Color(0xFFDC2626)
        ConnectionStatus.DISCONNECTED -> Color(0xFFF59E0B)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (compact) {
                    Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CodexTheme.colors.surface)
                        .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 6.dp else 8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            Text(
                text = when (state.connectionStatus) {
                    ConnectionStatus.CONNECTED -> "已连接 desktop gateway"
                    ConnectionStatus.CONNECTING -> "正在连接 desktop gateway"
                    ConnectionStatus.ERROR -> "连接异常"
                    ConnectionStatus.DISCONNECTED -> "未连接 desktop gateway"
                },
                color = CodexTheme.colors.textPrimary,
                fontSize = if (compact) 9.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.isGenerating) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "运行中",
                    color = Color(0xFF2563EB),
                    fontSize = if (compact) 8.sp else 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        if (state.connectionStatus == ConnectionStatus.DISCONNECTED || state.connectionStatus == ConnectionStatus.ERROR) {
            Spacer(Modifier.width(if (compact) 6.dp else 12.dp))
            Text(
                text = "连接",
                color = CodexTheme.colors.textPrimary,
                fontSize = if (compact) 8.sp else 10.sp,
                modifier = Modifier.clickable(onClick = onOpenConnection)
            )
        }
    }
}

@Composable
private fun EmptyThreadCard(
    connected: Boolean,
    hasThreads: Boolean
) {
    val title = when {
        !connected -> "连接 desktop gateway 后开始"
        hasThreads -> "当前会话暂无消息"
        else -> "暂无会话"
    }
    val detail = when {
        !connected -> "本 app 只显示 desktop 真实会话数据。"
        hasThreads -> "从下方输入区发送第一条消息。"
        else -> "点右上角新建，或在侧边栏选择已有会话。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = CodexTheme.colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = detail,
            color = CodexTheme.colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ThreadSwitchingCard(pendingTitle: String?) {
    val title = pendingTitle?.takeIf { it.isNotBlank() } ?: "会话"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "正在切换到 $title",
            color = CodexTheme.colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "等待 desktop 同步真实内容…",
            color = CodexTheme.colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ApprovalCard(
    text: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    compactMode: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFBEB))
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
            .padding(if (compactMode) 10.dp else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("!", color = Color(0xFFD97706), fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "待审批",
                color = CodexTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compactMode) 12.sp else 13.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            color = CodexTheme.colors.textPrimary,
            fontSize = if (compactMode) 12.sp else 13.sp,
            lineHeight = if (compactMode) 17.sp else 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(if (compactMode) 4.dp else 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(if (compactMode) 5.dp else 6.dp)) {
            ActionPill("允许", true, onClick = onApprove)
            ActionPill("拒绝", false, onClick = onReject)
        }
    }
}

@Composable
private fun ActionPill(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) CodexTheme.colors.textPrimary else CodexTheme.colors.surface)
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = CodexTheme.colors.border,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (filled) 7.dp else 6.dp)
    ) {
        Text(
            text = label,
            color = if (filled) Color.White else CodexTheme.colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MessageCard(message: ThreadMessage, compactMode: Boolean) {
    when (message.role) {
        MessageRole.USER -> UserMessage(message, compactMode)
        MessageRole.ASSISTANT -> AssistantMessage(message, compactMode)
        MessageRole.SYSTEM -> SystemMessage(message, compactMode)
    }
}

@Composable
private fun UserMessage(message: ThreadMessage, compactMode: Boolean) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(if (compactMode) 14.dp else 16.dp))
                .background(CodexTheme.colors.userBubble)
                .padding(horizontal = if (compactMode) 10.dp else 11.dp, vertical = if (compactMode) 8.dp else 9.dp)
        ) {
            message.blocks.forEach { block ->
                if (block is MessageBlock.Text) {
                    ExpandableText(
                        text = block.value,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        textColor = CodexTheme.colors.userBubbleText,
                        fontSize = if (compactMode) 12.sp else 13.sp,
                        lineHeight = if (compactMode) 17.sp else 18.sp,
                        maxCollapsedLines = if (compactMode) 4 else 5
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(message: ThreadMessage, compactMode: Boolean) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var reasoningExpanded by rememberSaveable(message.id + ":reasoning") { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
    ) {
        message.blocks.forEach { block ->
            when (block) {
                is MessageBlock.Text -> ExpandableText(
                    text = block.value,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    textColor = CodexTheme.colors.textPrimary,
                    fontSize = if (compactMode) 12.sp else 13.sp,
                    lineHeight = if (compactMode) 17.sp else 19.sp,
                    maxCollapsedLines = if (compactMode) 6 else 8
                )

                is MessageBlock.Code -> CodeBlock(block.language, block.value, compactMode)
                is MessageBlock.Status -> InlineStatus(block.value)
                is MessageBlock.Reasoning -> ReasoningBlock(
                    text = block.value,
                    expanded = reasoningExpanded,
                    onToggle = { reasoningExpanded = !reasoningExpanded },
                    compactMode = compactMode
                )
            }
        }
    }
}

@Composable
private fun ReasoningBlock(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    compactMode: Boolean
) {
    val displayText = text.trimEnd()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .padding(horizontal = if (compactMode) 9.dp else 10.dp, vertical = if (compactMode) 7.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onToggle)
        ) {
            Text(
                text = if (expanded) "思考详情" else "思考中",
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (expanded) "收起" else "展开",
                color = CodexTheme.colors.textTertiary,
                fontSize = 10.sp
            )
        }
        if (expanded) {
            Text(
                text = displayText,
                color = CodexTheme.colors.textPrimary,
                fontSize = if (compactMode) 11.sp else 12.sp,
                lineHeight = if (compactMode) 15.sp else 17.sp
            )
        }
    }
}

@Composable
private fun ExpandableText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    maxCollapsedLines: Int
) {
    val displayText = text.trimEnd()
    val shouldCollapse = displayText.length > 120 || displayText.count { it == '\n' } >= 3
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = displayText,
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = if (shouldCollapse && !expanded) maxCollapsedLines else Int.MAX_VALUE,
            overflow = if (shouldCollapse && !expanded) TextOverflow.Ellipsis else TextOverflow.Clip
        )
        if (shouldCollapse) {
            Text(
                text = if (expanded) "收起" else "展开",
                color = CodexTheme.colors.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onToggle)
            )
        }
    }
}

@Composable
private fun SystemMessage(message: ThreadMessage, compactMode: Boolean) {
    val text = message.blocks.filterIsInstance<MessageBlock.Status>().firstOrNull()?.value ?: return
    InlineStatus(text, compactMode)
}

@Composable
private fun InlineStatus(text: String, compactMode: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("⏸", color = CodexTheme.colors.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = CodexTheme.colors.textSecondary,
            fontSize = if (compactMode) 10.sp else 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CodeBlock(language: String, value: String, compactMode: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .background(CodexTheme.colors.codeBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compactMode) 9.dp else 10.dp, vertical = if (compactMode) 5.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language,
                color = Color(0xFFD1D5DB),
                fontSize = if (compactMode) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Divider(color = Color(0xFF374151))
        Text(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (compactMode) 9.dp else 10.dp, vertical = if (compactMode) 7.dp else 8.dp),
            color = Color(0xFFE5E7EB),
            fontSize = if (compactMode) 10.sp else 11.sp,
            lineHeight = if (compactMode) 15.sp else 16.sp
        )
    }
}

@Composable
private fun Composer(
    state: HomeUiState,
    compactMode: Boolean,
    activePanel: String,
    onActivePanelChange: (String) -> Unit,
    onToggleCompact: () -> Unit,
    onToggleDetails: () -> Unit,
    onChange: (String) -> Unit,
    onInsertText: (String) -> Unit,
    onApplySlashCommand: (String) -> Unit,
    onClearComposer: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    var dismissImeAfterSend by rememberSaveable { mutableStateOf(false) }
    var slashQuery by rememberSaveable { mutableStateOf("") }
    var suppressInlineSlashPanel by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val rootView = LocalView.current
    val inputInteractionSource = remember { MutableInteractionSource() }
    var composerFieldValue by remember(state.selectedThreadId, state.pendingThreadTitle) {
        mutableStateOf(TextFieldValue(state.composerText, TextRange(state.composerText.length)))
    }
    val normalizedComposer = state.composerText.trimEnd()
    val lastSeparator = normalizedComposer.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
    val trailingToken = if (lastSeparator >= 0) normalizedComposer.substring(lastSeparator + 1) else normalizedComposer
    val slashPanelVisible = state.slashCommands.isNotEmpty() &&
        (activePanel == "slash" || (!suppressInlineSlashPanel && (trailingToken.startsWith("/") || trailingToken.startsWith("!"))))
    val filteredCommands = state.slashCommands.filter { command ->
        val keyword = command.substringBefore("  ").trim()
        slashQuery.isBlank() ||
            command.contains(slashQuery, ignoreCase = true) ||
            keyword.contains(slashQuery, ignoreCase = true)
    }
    val composerEnabled = !state.isThreadSwitching
    val sendEnabled = composerEnabled &&
        state.composerText.isNotBlank() &&
        state.connectionStatus == ConnectionStatus.CONNECTED
    LaunchedEffect(activePanel) {
        if (activePanel == "context" || activePanel == "files") {
            onActivePanelChange("none")
        }
    }
    LaunchedEffect(dismissImeAfterSend, state.composerText) {
        if (dismissImeAfterSend && state.composerText.isBlank()) {
            onActivePanelChange("none")
            slashQuery = ""
            suppressInlineSlashPanel = false
            focusManager.clearFocus(force = true)
            rootView.clearFocus()
            keyboardController?.hide()
            dismissImeAfterSend = false
        }
    }
    LaunchedEffect(state.composerText) {
        if (composerFieldValue.text != state.composerText) {
            composerFieldValue = TextFieldValue(state.composerText, TextRange(state.composerText.length))
        }
        if (state.composerText.isBlank()) {
            suppressInlineSlashPanel = false
        }
    }
    fun focusComposer(showKeyboard: Boolean = true) {
        focusRequester.requestFocus()
        if (showKeyboard) {
            keyboardController?.show()
        }
    }
    val sendNow: () -> Unit = {
        if (sendEnabled) {
            dismissImeAfterSend = true
            onSend()
        } else if (state.connectionStatus != ConnectionStatus.CONNECTED) {
            focusManager.clearFocus(force = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodexTheme.colors.surface)
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        AnimatedVisibility(visible = state.showComposerDetails) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    MiniAction("清空", "■") {
                        onActivePanelChange("none")
                        slashQuery = ""
                        suppressInlineSlashPanel = false
                        onClearComposer()
                    }
                    MiniAction("/命令", "⌕") {
                        val opening = activePanel != "slash"
                        suppressInlineSlashPanel = false
                        onActivePanelChange(if (opening) "slash" else "none")
                        if (opening) {
                            slashQuery = if (trailingToken.startsWith("/") || trailingToken.startsWith("!")) trailingToken else ""
                            focusComposer()
                        }
                    }
                }
                if (state.cwd.isNotBlank() || state.permissionSummary.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.cwd.isNotBlank()) {
                            Text(
                                text = state.cwd,
                                color = CodexTheme.colors.textSecondary,
                                fontSize = if (compactMode) 10.sp else 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (state.permissionSummary.isNotBlank()) {
                            Text(
                                text = state.permissionSummary,
                                color = CodexTheme.colors.textTertiary,
                                fontSize = if (compactMode) 10.sp else 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = slashPanelVisible) {
                    SlashCommandPanel(
                        query = slashQuery,
                        commands = filteredCommands,
                        onQueryChange = { slashQuery = it },
                        onSelect = { command ->
                            suppressInlineSlashPanel = false
                            onApplySlashCommand(command.substringBefore("  ").trim())
                            onActivePanelChange("none")
                            slashQuery = ""
                            focusComposer()
                        }
                    )
                }
            }
        }
        if (state.showComposerDetails) {
            Divider(color = CodexTheme.colors.border, thickness = 1.dp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CodexTheme.colors.surface)
                .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(22.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ComposerIconButton(
                onClick = onToggleDetails,
                size = 32.dp,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("+", color = CodexTheme.colors.textPrimary, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 32.dp)
                    .clickable(
                        enabled = composerEnabled,
                        interactionSource = inputInteractionSource,
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    .padding(horizontal = 2.dp, vertical = 0.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = composerFieldValue,
                    onValueChange = {
                        if (!composerEnabled) {
                            return@BasicTextField
                        }
                        composerFieldValue = it
                        suppressInlineSlashPanel = false
                        onChange(it.text)
                        val trimmed = it.text.trimEnd()
                        val separator = trimmed.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
                        val token = if (separator >= 0) trimmed.substring(separator + 1) else trimmed
                        if (token.startsWith("/") || token.startsWith("!")) {
                            onActivePanelChange("slash")
                            slashQuery = token
                        } else if (activePanel == "slash") {
                            onActivePanelChange("none")
                            slashQuery = ""
                        }
                    },
                    minLines = 1,
                    maxLines = if (compactMode) 4 else 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { sendNow() },
                        onDone = { sendNow() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    enabled = composerEnabled,
                    textStyle = TextStyle(
                        color = if (composerEnabled) CodexTheme.colors.textPrimary else CodexTheme.colors.textTertiary,
                        fontSize = if (compactMode) 11.sp else 12.sp,
                        lineHeight = if (compactMode) 14.sp else 15.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    decorationBox = { inner ->
                        if (composerFieldValue.text.isEmpty()) {
                            Text(
                                when {
                                    !composerEnabled -> "正在切换会话…"
                                    state.connectionStatus == ConnectionStatus.CONNECTED -> "回复 Codex"
                                    state.connectionStatus == ConnectionStatus.CONNECTING -> "正在连接…"
                                    else -> "未连接"
                                },
                                color = CodexTheme.colors.textTertiary,
                                fontSize = if (compactMode) 11.sp else 13.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 32.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            inner()
                        }
                    }
                )
            }
            if (state.isGenerating) {
                ComposerIconButton(
                    onClick = onStop,
                    size = 32.dp,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("■", color = CodexTheme.colors.textPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            ComposerIconButton(
                onClick = {
                    if (sendEnabled) sendNow()
                },
                size = 32.dp,
                shape = CircleShape,
                fill = when {
                    sendEnabled -> Color(0xFF111827)
                    else -> CodexTheme.colors.surfaceSubtle
                }
            ) {
                Text(
                    "➤",
                    color = if (!sendEnabled) {
                        CodexTheme.colors.textTertiary
                    } else {
                        Color.White
                    },
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun ThreadMessage.revisionKey(): String {
    val contentSize = blocks.sumOf { block: MessageBlock ->
        when (block) {
            is MessageBlock.Text -> block.value.length
            is MessageBlock.Code -> block.language.length + block.value.length
            is MessageBlock.Status -> block.value.length
            is MessageBlock.Reasoning -> block.value.length
        }
    }
    return "$id:${blocks.size}:$contentSize"
}

@Composable
private fun ContextPanel(
    chips: List<ComposerChip>,
    cwd: String,
    permissionSummary: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (chips.isNotEmpty()) {
            chips.forEach { chip ->
                SlashCommandRow(
                    command = chip.label,
                    supporting = if (chip.icon == ComposerChipIcon.CONTEXT) "上下文信息" else "文件引用"
                )
            }
        }
        if (cwd.isNotBlank()) {
            SlashCommandRow(command = cwd, supporting = "当前工作目录")
        }
        if (permissionSummary.isNotBlank()) {
            SlashCommandRow(command = permissionSummary, supporting = "当前权限")
        }
    }
}

@Composable
private fun FilePanel(
    chips: List<ComposerChip>,
    cwd: String,
    onInsertText: (String) -> Unit
) {
    val fileChips = chips.filter { it.icon == ComposerChipIcon.FILE }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (fileChips.isEmpty()) {
            SlashCommandRow(command = if (cwd.isBlank()) "暂无文件上下文" else cwd, supporting = "等待 gateway 文件索引")
        } else {
            fileChips.forEach { chip ->
                SlashCommandRow(
                    command = chip.label,
                    supporting = "点击插入到输入框",
                    onClick = { onInsertText(chip.label) }
                )
            }
        }
    }
}

@Composable
private fun SlashCommandPanel(
    query: String,
    commands: List<String>,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .heightIn(max = 180.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CodexTheme.colors.surfaceSubtle)
                .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", color = CodexTheme.colors.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = CodexTheme.colors.textPrimary,
                    fontSize = 12.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) {
                        Text("搜索命令", color = CodexTheme.colors.textTertiary, fontSize = 12.sp)
                    }
                    innerTextField()
                }
            )
        }
                if (commands.isEmpty()) {
            SlashCommandRow(command = "没有匹配的命令", supporting = "修改搜索词")
        } else {
            commands.forEach { command ->
                SlashCommandRow(
                    command = command.substringBefore("  ").trim(),
                    supporting = command.substringAfter("  ", ""),
                    onClick = { onSelect(command) }
                )
            }
        }
    }
}

@Composable
private fun SlashCommandRow(
    command: String,
    supporting: String = "",
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = command,
            color = CodexTheme.colors.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (supporting.isNotBlank()) {
            Text(
                text = supporting,
                color = CodexTheme.colors.textSecondary,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniAction(
    label: String,
    icon: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = CodexTheme.colors.textSecondary, fontSize = 9.sp)
        Spacer(Modifier.width(4.dp))
        Text(text = label, color = CodexTheme.colors.textPrimary, fontSize = 10.sp)
    }
}

@Composable
private fun ComposerIconButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
    fill: Color = CodexTheme.colors.surfaceSubtle,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
