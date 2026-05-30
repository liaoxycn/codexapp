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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import com.codex.mobile.model.ThreadGroupKind
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
                    val selectedThreadStatus = when {
                        state.selectedThreadId.isBlank() -> ThreadStatus.IDLE
                        else -> selectedThread?.status ?: ThreadStatus.IDLE
                    }
                    TopBar(
                        title = selectedThread?.title ?: "Codex",
                        status = selectedThreadStatus,
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
                onLoadOlderMessages = viewModel::loadOlderMessages,
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
                text = "连接 Desktop Gateway",
                color = CodexTheme.colors.textPrimary,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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
                    text = "移动端只负责连接与展示；账号、Key、MCP、Skill 均由桌面端处理。",
                    color = CodexTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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
                        Text("断开", color = CodexTheme.colors.danger)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = CodexTheme.colors.textPrimary)
                }
            }
        }
    )
}

@Composable
private fun TopBar(
    title: String,
    status: ThreadStatus,
    onOpenDrawer: () -> Unit,
    onCreateThread: () -> Unit,
    onOpenConnection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodexTheme.colors.surface)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderIconButton(
            icon = Icons.Filled.Menu,
            contentDescription = "打开抽屉",
            onClick = onOpenDrawer
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThreadStatusIcon(status)
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = CodexTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        HeaderIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "新建会话",
            onClick = onCreateThread
        )
        HeaderIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "连接设置",
            onClick = onOpenConnection
        )
    }
    Divider(color = CodexTheme.colors.border)
}

@Composable
private fun ThreadStatusIcon(status: ThreadStatus) {
    Box(
        modifier = Modifier.semantics {
            contentDescription = "当前会话状态：${threadStatusLabel(status)}"
        }
    ) {
        StatusDot(status, size = 8.dp)
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
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
    var expandedProjectGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    val normalizedQuery = query.trim()
    val matches: (ThreadSummary) -> Boolean = { thread ->
        normalizedQuery.isBlank() ||
            thread.title.contains(normalizedQuery, ignoreCase = true) ||
            thread.preview.contains(normalizedQuery, ignoreCase = true)
    }
    val activeThreads = state.threads.filter(matches)
    val projectThreads = activeThreads.filter { it.groupKind == ThreadGroupKind.PROJECT && it.groupLabel.isNotBlank() }
    val chatThreads = activeThreads.filter { it.groupKind != ThreadGroupKind.PROJECT || it.groupLabel.isBlank() }
    val selectedThread = state.threads.firstOrNull { it.id == state.selectedThreadId }
    val currentProjectGroupLabel = selectedThread?.let { thread ->
        if (thread.groupKind == ThreadGroupKind.PROJECT && thread.groupLabel.isNotBlank()) {
            thread.groupLabel
        } else {
            null
        }
    }
    val groupedProjectThreads = projectThreads
        .sortedWith(threadListSortOrder())
        .groupBy { thread ->
            thread.groupLabel
        }
        .filterValues { threads -> threads.isNotEmpty() }
    val orderedProjectGroups = groupedProjectThreads.keys.sortedWith(
        compareByDescending<String> { groupLabel ->
            groupedProjectThreads[groupLabel].orEmpty().maxOfOrNull { it.updatedAt } ?: 0L
        }.thenBy<String> { groupLabel -> groupLabel }
    )

    LaunchedEffect(orderedProjectGroups) {
        expandedProjectGroups = expandedProjectGroups + orderedProjectGroups
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .width(304.dp)
            .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .background(CodexTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 10.dp)
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
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                ConnectionStatusLine(
                    status = state.connectionStatus,
                    detail = state.connectionDetail
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
        Spacer(Modifier.height(10.dp))
        DrawerSearchBar(
            query = query,
            onChange = { query = it }
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                SectionHeader(text = "项目", startPadding = 10.dp)
            }
            if (projectThreads.isEmpty()) {
                item {
                    Text(
                        text = "暂无项目会话",
                        color = CodexTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            } else {
                orderedProjectGroups.forEach { groupLabel ->
                    val threads = groupedProjectThreads[groupLabel].orEmpty()
                    val isExpanded = groupLabel == currentProjectGroupLabel || expandedProjectGroups.contains(groupLabel)
                    item {
                        GroupHeader(
                            label = groupLabel,
                            icon = Icons.Filled.Folder,
                            compact = true,
                            expanded = isExpanded,
                            onToggle = if (groupLabel == currentProjectGroupLabel) null else ({
                                expandedProjectGroups = if (isExpanded) {
                                    expandedProjectGroups - groupLabel
                                } else {
                                    expandedProjectGroups + groupLabel
                                }
                            })
                        )
                    }
                    if (isExpanded) {
                        items(threads) { thread ->
                            ThreadRow(
                                summary = thread,
                                selected = thread.id == state.selectedThreadId,
                                indentLevel = 1,
                                onClick = { onSelectThread(thread.id) }
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(3.dp))
                SectionHeader(text = "会话", startPadding = 10.dp)
            }
            if (chatThreads.isEmpty()) {
                item {
                    Text(
                        text = "暂无普通会话",
                        color = CodexTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            } else {
                items(chatThreads) { thread ->
                    ThreadRow(
                        summary = thread,
                        selected = thread.id == state.selectedThreadId,
                        indentLevel = 0,
                        onClick = { onSelectThread(thread.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    startPadding: androidx.compose.ui.unit.Dp
) {
    Text(
        text = text,
        modifier = Modifier.padding(start = startPadding),
        color = CodexTheme.colors.textSecondary,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
}

@Composable
private fun ConnectionStatusLine(
    status: ConnectionStatus,
    detail: String
) {
    val statusText = when (status) {
        ConnectionStatus.CONNECTED -> "已连接 Desktop Gateway"
        ConnectionStatus.CONNECTING -> "正在连接 Desktop Gateway"
        ConnectionStatus.ERROR -> detail.ifBlank { "连接异常" }
        ConnectionStatus.DISCONNECTED -> "未连接 Desktop Gateway"
    }
    Text(
        text = statusText,
        color = when (status) {
            ConnectionStatus.CONNECTED -> Color(0xFF059669)
            ConnectionStatus.CONNECTING -> Color(0xFF2563EB)
            ConnectionStatus.ERROR -> Color(0xFFDC2626)
            ConnectionStatus.DISCONNECTED -> Color(0xFFF59E0B)
        },
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
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
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodexTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun GroupHeader(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    compact: Boolean = false,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 3.dp)
            .padding(start = if (compact) 10.dp else 10.dp)
            .clickable(enabled = onToggle != null) { onToggle?.invoke() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CodexTheme.colors.textTertiary,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compact) 12.sp else 13.sp,
                lineHeight = if (compact) 15.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        if (expanded != null) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = CodexTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp)
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = CodexTheme.colors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "搜索会话" },
            decorationBox = { innerTextField ->
                if (query.isBlank()) {
                    Text("搜索会话", color = CodexTheme.colors.textTertiary, fontSize = 12.sp)
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
    indentLevel: Int,
    onClick: () -> Unit
) {
    val startPadding = 10.dp + (indentLevel * 8).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) CodexTheme.colors.surfaceSubtle else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = startPadding, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CodexTheme.colors.textTertiary)
                )
            }
        }
        StatusDot(summary.status)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.title,
                    modifier = Modifier.weight(1f),
                    color = CodexTheme.colors.textPrimary,
                    style = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ThreadStatusText(
                    status = summary.status,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.preview.ifBlank { summary.title },
                    modifier = Modifier.weight(1f),
                    color = CodexTheme.colors.textSecondary,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary.updatedAt > 0L) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = formatThreadUpdatedAt(summary.updatedAt),
                        color = CodexTheme.colors.textTertiary,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadStatusText(
    status: ThreadStatus,
    modifier: Modifier = Modifier
) {
    val text = threadStatusLabel(status)
    val color = when {
        status == ThreadStatus.RUNNING -> Color(0xFF2563EB)
        status == ThreadStatus.NEEDS_APPROVAL -> Color(0xFFF59E0B)
        status == ThreadStatus.FAILED -> Color(0xFFDC2626)
        else -> CodexTheme.colors.textTertiary
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun threadStatusLabel(status: ThreadStatus): String = when (status) {
    ThreadStatus.RUNNING -> "运行中"
    ThreadStatus.NEEDS_APPROVAL -> "待审批"
    ThreadStatus.FAILED -> "失败"
    ThreadStatus.IDLE -> "空闲"
}

@Composable
private fun StatusDot(status: ThreadStatus, size: Dp = 7.dp) {
    val color = threadStatusColor(status)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

private fun threadStatusColor(status: ThreadStatus): Color {
    return when (status) {
        ThreadStatus.RUNNING -> Color(0xFF2563EB)
        ThreadStatus.IDLE -> Color(0xFF9CA3AF)
        ThreadStatus.NEEDS_APPROVAL -> Color(0xFFF59E0B)
        ThreadStatus.FAILED -> Color(0xFFDC2626)
    }
}

internal fun threadListSortOrder(): Comparator<ThreadSummary> {
    return compareByDescending<ThreadSummary> { it.updatedAt }
        .thenByDescending { it.id }
}

internal fun formatThreadUpdatedAt(updatedAt: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val deltaMinutes = ((nowMillis - updatedAt).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        deltaMinutes <= 0 -> "刚刚"
        deltaMinutes < 60 -> "${deltaMinutes}分前"
        deltaMinutes < 24 * 60 -> {
            val hours = deltaMinutes / 60
            "${hours}小时前"
        }
        else -> {
            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                .format(java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()))
        }
    }
}

@Composable
internal fun ThreadScreen(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    compactMode: Boolean,
    onOpenConnection: () -> Unit,
    onRefreshCurrent: () -> Unit,
    onLoadOlderMessages: () -> Unit,
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
    val isLoadingOlder = state.isLoadingOlder
    val messageItemStartIndex = if (state.hasMoreHistory) 1 else 0
    val contentItemCount = (if (state.messages.isEmpty()) 1 else state.messages.size) +
        (if (state.pendingApproval != null) 1 else 0)
    val totalItems = messageItemStartIndex + contentItemCount
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
    var pullGestureTick by rememberSaveable(state.selectedThreadId) { mutableIntStateOf(0) }
    val showPullHint = pullDistance > 0f || state.isManualRefreshing || SystemClock.uptimeMillis() < pullHintVisibleUntil
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val hasVisibleMessages = state.messages.isNotEmpty() && !state.isThreadSwitching
    val showConnectionBanner = state.connectionStatus == ConnectionStatus.DISCONNECTED ||
        state.connectionStatus == ConnectionStatus.ERROR
    val showTopLoadHint = state.hasMoreHistory && hasVisibleMessages && (isLoadingOlder || (isAtTop && pullDistance > 0f))
    val isAtBottom by remember {
        derivedStateOf {
            listState.layoutInfo.totalItemsCount == 0 || !listState.canScrollForward
        }
    }
    val showJumpToBottom = hasVisibleMessages && !isAtBottom
    val userWasAtBottom = rememberSaveable(state.selectedThreadId) { mutableStateOf(true) }
    val pullConnection = remember(state.selectedThreadId, state.isGenerating, state.isManualRefreshing, lastItemIndex, isAtBottom) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (state.isGenerating || state.isManualRefreshing) {
                    return Offset.Zero
                }
                if (!isAtBottom) {
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
                    pullGestureTick += 1
                } else if (available.y > 0f && pullDistance > 0f) {
                    pullDistance = 0f
                    pullVelocity = 0f
                    pullGestureTick += 1
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                if (isAtBottom && !state.isGenerating && !state.isManualRefreshing && pullDistance >= pullThreshold) {
                    pullHintVisibleUntil = SystemClock.uptimeMillis() + 700L
                    onRefreshCurrent()
                    pullDistance = 0f
                    pullVelocity = 0f
                    return Velocity.Zero
                }
                pullVelocity = 0f
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(pullGestureTick, state.selectedThreadId) {
        if (pullDistance <= 0f || state.isManualRefreshing || state.isGenerating) return@LaunchedEffect
        val expectedTick = pullGestureTick
        kotlinx.coroutines.delay(900L)
        if (expectedTick == pullGestureTick && !state.isManualRefreshing && !state.isGenerating && pullDistance > 0f) {
            pullDistance = 0f
            pullVelocity = 0f
        }
    }

    LaunchedEffect(isAtBottom) {
        userWasAtBottom.value = isAtBottom
    }

    LaunchedEffect(lastMessageRevision, state.pendingApproval, state.selectedThreadId, state.isGenerating, state.isManualRefreshing, state.isLoadingOlder) {
        if (!state.messages.isNotEmpty()) return@LaunchedEffect
        if (state.isManualRefreshing || state.isLoadingOlder || state.pendingApproval != null) return@LaunchedEffect
        if (!userWasAtBottom.value) return@LaunchedEffect
        if (state.isGenerating) {
            listState.scrollToItem(lastItemIndex)
        } else {
            listState.animateScrollToItem(lastItemIndex)
        }
    }

    var topLoadArmed by rememberSaveable(state.selectedThreadId) { mutableStateOf(false) }
    var topLoadAnchorId by rememberSaveable(state.selectedThreadId) { mutableStateOf<String?>(null) }
    var topLoadAnchorOffset by rememberSaveable(state.selectedThreadId) { mutableIntStateOf(0) }
    LaunchedEffect(
        state.selectedThreadId,
        isAtTop,
        state.hasMoreHistory,
        isLoadingOlder,
        state.isThreadSwitching,
        state.messages.size,
        listState.isScrollInProgress
    ) {
        if (!state.hasMoreHistory || state.isThreadSwitching || state.messages.isEmpty()) {
            topLoadArmed = false
            topLoadAnchorId = null
            return@LaunchedEffect
        }
        if (!isAtTop) {
            topLoadArmed = true
            return@LaunchedEffect
        }
        if (!(state.hasMoreHistory &&
                state.messages.isNotEmpty() &&
                !state.isThreadSwitching &&
                isAtTop &&
                topLoadArmed &&
                !isLoadingOlder &&
                !listState.isScrollInProgress)
        ) {
            return@LaunchedEffect
        }
        val anchorInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.index >= messageItemStartIndex && info.index < messageItemStartIndex + state.messages.size
        }
        if (anchorInfo != null) {
            val messageIndex = anchorInfo.index - messageItemStartIndex
            if (messageIndex in state.messages.indices) {
                topLoadAnchorId = state.messages[messageIndex].id
                topLoadAnchorOffset = anchorInfo.offset
            }
        } else {
            topLoadAnchorId = null
        }
        topLoadArmed = false
        onLoadOlderMessages()
    }

    LaunchedEffect(state.isLoadingOlder, state.messages.size, state.hasMoreHistory, state.selectedThreadId) {
        if (state.isLoadingOlder) return@LaunchedEffect
        val anchorId = topLoadAnchorId ?: return@LaunchedEffect
        val anchorIndex = state.messages.indexOfFirst { it.id == anchorId }
        if (anchorIndex >= 0 && state.messages.isNotEmpty()) {
            val restoredStartIndex = if (state.hasMoreHistory) 1 else 0
            listState.scrollToItem(restoredStartIndex + anchorIndex, topLoadAnchorOffset)
        }
        topLoadAnchorId = null
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CodexTheme.colors.background)
            .nestedScroll(pullConnection)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("thread_message_list"),
            state = listState,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = if (showConnectionBanner) 58.dp else 2.dp,
                bottom = composerPadding + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 5.dp)
        ) {
            if (state.hasMoreHistory) {
                item {
                    HistoryLoadHint(
                        loading = isLoadingOlder,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
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

            itemsIndexed(
                items = state.messages,
                key = { _, message -> message.id }
            ) { index, message ->
                MessageCard(message, compactMode, index)
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
        if (showConnectionBanner) {
            ConnectionBanner(
                state = state,
                compact = compactMode,
                onOpenConnection = onOpenConnection,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
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
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = composerPadding + 18.dp),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120))
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CodexTheme.colors.surface.copy(alpha = 0.96f))
                    .border(1.dp, CodexTheme.colors.border, CircleShape)
                    .testTag("jump_to_bottom_button")
            ) {
                IconButton(
                    onClick = {
                        if (state.messages.isNotEmpty()) {
                            scope.launch {
                                listState.animateScrollToItem(lastItemIndex)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "滚到底部" }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = CodexTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
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
            val refreshLabel = when {
                refreshing || generating -> "刷新会话中"
                progress >= 1f -> "松开刷新"
                else -> "继续上滑"
            }
            Text(
                text = refreshLabel,
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(CodexTheme.colors.surface.copy(alpha = 0.92f))
                    .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(999.dp))
                    .testTag("pull_refresh_hint")
                    .semantics { contentDescription = refreshLabel }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ConnectionBanner(
    state: HomeUiState,
    compact: Boolean,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (state.connectionStatus) {
        ConnectionStatus.CONNECTED -> "已连接 Desktop Gateway"
        ConnectionStatus.CONNECTING -> "正在连接 Desktop Gateway"
        ConnectionStatus.ERROR -> "连接异常"
        ConnectionStatus.DISCONNECTED -> "未连接 Desktop Gateway"
    }
    val statusColor = when (state.connectionStatus) {
        ConnectionStatus.CONNECTED -> Color(0xFF059669)
        ConnectionStatus.CONNECTING -> Color(0xFF2563EB)
        ConnectionStatus.ERROR -> Color(0xFFDC2626)
        ConnectionStatus.DISCONNECTED -> Color(0xFFF59E0B)
    }
    Row(
        modifier = modifier
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
            )
            .semantics { contentDescription = "连接状态：$statusText" },
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
                text = statusText,
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(CodexTheme.colors.surfaceSubtle)
                    .clickable(onClick = onOpenConnection)
                    .clearAndSetSemantics { contentDescription = "打开连接设置" }
                    .defaultMinSize(minHeight = if (compact) 32.dp else 36.dp)
                    .padding(horizontal = if (compact) 9.dp else 11.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "连接",
                    color = CodexTheme.colors.textPrimary,
                    fontSize = if (compact) 10.sp else 11.sp,
                    lineHeight = if (compact) 13.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
    }
}

@Composable
private fun EmptyThreadCard(
    connected: Boolean,
    hasThreads: Boolean
) {
    val title = when {
        !connected -> "连接 Desktop Gateway 后开始"
        hasThreads -> "当前会话暂无消息"
        else -> "暂无会话"
    }
    val detail = when {
        !connected -> "本 app 只显示 Desktop Gateway 真实会话数据。"
        hasThreads -> "从下方输入区发送第一条消息。"
        else -> "点右上角新建，或在侧边栏选择已有会话。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = if (connected) Icons.Filled.Info else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = CodexTheme.colors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Text(
            text = detail,
            color = CodexTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ThreadSwitchingCard(pendingTitle: String?) {
    val title = pendingTitle?.takeIf { it.isNotBlank() } ?: "会话"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.HourglassEmpty,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "正在切换到 $title",
            color = CodexTheme.colors.textPrimary,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Text(
            text = "等待 Desktop Gateway 同步真实内容…",
            color = CodexTheme.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
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
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "待审批",
                color = CodexTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compactMode) 12.sp else 13.sp,
                lineHeight = if (compactMode) 16.sp else 17.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 12.dp, vertical = if (filled) 7.dp else 6.dp)
    ) {
        Text(
            text = label,
            color = if (filled) Color.White else CodexTheme.colors.textPrimary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun MessageCard(message: ThreadMessage, compactMode: Boolean, messageIndex: Int) {
    when (message.role) {
        MessageRole.USER -> UserMessage(message, compactMode)
        MessageRole.ASSISTANT -> AssistantMessage(message, compactMode, messageIndex)
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
                    MarkdownText(
                        text = block.value,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        textColor = CodexTheme.colors.userBubbleText,
                        fontSize = if (compactMode) 12.sp else 13.sp,
                        lineHeight = if (compactMode) 17.sp else 18.sp,
                        maxCollapsedLines = if (compactMode) 4 else 5,
                        wrapContent = true
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(message: ThreadMessage, compactMode: Boolean, messageIndex: Int) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var reasoningExpanded by rememberSaveable(message.id + ":reasoning") { mutableStateOf(false) }
    val commandSummary = message.blocks.filterIsInstance<MessageBlock.CommandSummary>().firstOrNull()?.value
    val commandMetaLines = message.blocks.filterIsInstance<MessageBlock.CommandMeta>().mapNotNull { it.value.trim().takeIf(String::isNotEmpty) }
    val commandOutput = message.blocks.filterIsInstance<MessageBlock.Code>().firstOrNull { it.language.equals("shell", ignoreCase = true) }
    val hasCommandCard = commandSummary != null || commandMetaLines.isNotEmpty() || commandOutput != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp)
    ) {
        if (hasCommandCard) {
            CommandExecutionCard(
                messageId = message.id,
                blockIndex = messageIndex,
                summary = commandSummary ?: if (commandOutput != null) "命令执行中" else "命令状态",
                metaLines = commandMetaLines,
                outputLanguage = commandOutput?.language ?: "shell",
                outputValue = commandOutput?.value.orEmpty(),
                compactMode = compactMode
            )
        }

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

                is MessageBlock.Code -> if (!block.language.equals("shell", ignoreCase = true)) {
                    CodeBlock(
                        messageId = message.id,
                        blockIndex = messageIndex,
                        language = block.language,
                        value = block.value,
                        compactMode = compactMode
                    )
                }

                is MessageBlock.Status -> InlineStatus(block.value, compactMode)
                is MessageBlock.Reasoning -> ReasoningBlock(
                    text = block.value,
                    expanded = reasoningExpanded,
                    onToggle = { reasoningExpanded = !reasoningExpanded },
                    compactMode = compactMode
                )
                is MessageBlock.CommandSummary, is MessageBlock.CommandMeta -> Unit
            }
        }
    }
}

@Composable
private fun CommandExecutionCard(
    messageId: String,
    blockIndex: Int,
    summary: String,
    metaLines: List<String>,
    outputLanguage: String,
    outputValue: String,
    compactMode: Boolean
) {
    val hasOutput = outputValue.isNotBlank()
    val hasDetails = metaLines.isNotEmpty() || hasOutput
    var detailsExpanded by rememberSaveable(messageId + ":command:$blockIndex") { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .border(1.dp, CodexTheme.colors.border, RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .padding(horizontal = if (compactMode) 8.dp else 9.dp, vertical = if (compactMode) 5.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (hasDetails) {
                Modifier
                    .clickable(onClick = { detailsExpanded = !detailsExpanded })
                    .clearAndSetSemantics { contentDescription = if (detailsExpanded) "收起命令详情" else "展开命令详情" }
            } else {
                Modifier
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = CodexTheme.colors.textTertiary,
                modifier = Modifier.size(if (compactMode) 13.dp else 15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = summary,
                color = CodexTheme.colors.textPrimary,
                fontSize = if (compactMode) 11.sp else 12.sp,
                lineHeight = if (compactMode) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasDetails) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (detailsExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (detailsExpanded) {
            metaLines.forEach { metaLine ->
                MarkdownText(
                    text = metaLine,
                    expanded = true,
                    onToggle = {},
                    textColor = CodexTheme.colors.textTertiary,
                    fontSize = if (compactMode) 9.sp else 10.sp,
                    lineHeight = if (compactMode) 12.sp else 13.sp,
                    maxCollapsedLines = Int.MAX_VALUE,
                    wrapContent = false
                )
            }
        }
        if (detailsExpanded && hasOutput) {
            CommandOutputBlock(
                messageId = messageId,
                blockIndex = blockIndex,
                language = outputLanguage,
                value = outputValue,
                compactMode = compactMode
            )
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
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onToggle)
                .clearAndSetSemantics { contentDescription = if (expanded) "收起思考详情" else "展开思考详情" }
                .padding(horizontal = 2.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (expanded) "思考详情" else "思考中",
                color = CodexTheme.colors.textSecondary,
                fontSize = if (compactMode) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CodexTheme.colors.textTertiary,
                modifier = Modifier.size(14.dp)
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
    val lines = remember(displayText) { parseMarkdownLines(displayText) }
    val shouldCollapse = lines.size > maxCollapsedLines || displayText.length > 140
    val visibleLines = if (shouldCollapse && !expanded) lines.take(maxCollapsedLines) else lines
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        visibleLines.forEach { line ->
            MarkdownLineItem(
                line = line,
                textColor = textColor,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
        }
        if (shouldCollapse) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onToggle)
                    .clearAndSetSemantics { contentDescription = if (expanded) "收起全文" else "展开全文" }
                    .defaultMinSize(minHeight = 32.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    color = CodexTheme.colors.textTertiary,
                    fontSize = 11.sp
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(13.dp)
                )
            }
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
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = CodexTheme.colors.textTertiary,
            modifier = Modifier.size(if (compactMode) 12.dp else 13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = CodexTheme.colors.textSecondary,
            fontSize = if (compactMode) 10.sp else 11.sp,
            lineHeight = if (compactMode) 14.sp else 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CodeBlock(
    messageId: String,
    blockIndex: Int,
    language: String,
    value: String,
    compactMode: Boolean = false
) {
    val isDiff = language.equals("diff", ignoreCase = true)
    val isShell = language.equals("shell", ignoreCase = true)
    val lineCount = remember(value) { value.lineSequence().count().coerceAtLeast(1) }
    val shouldCollapse = isShell || isDiff || lineCount > 8
    var expanded by rememberSaveable(messageId + ":code:$blockIndex") { mutableStateOf(!shouldCollapse) }
    val visibleText = if (shouldCollapse && !expanded) {
        value.lineSequence().take(if (isShell) 1 else 5).joinToString("\n")
    } else {
        value
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compactMode) 10.dp else 12.dp))
            .background(CodexTheme.colors.codeBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compactMode) 9.dp else 10.dp, vertical = if (compactMode) 6.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF374151))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isShell) "输出" else language.ifBlank { "code" },
                    color = Color(0xFFE5E7EB),
                    fontSize = if (compactMode) 9.sp else 10.sp,
                    lineHeight = if (compactMode) 11.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            if (shouldCollapse) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = { expanded = !expanded })
                        .clearAndSetSemantics { contentDescription = if (expanded) "收起代码块" else "展开代码块" }
                        .defaultMinSize(minHeight = 32.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "收起" else "展开",
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Divider(color = Color(0xFF374151))
        if (shouldCollapse && !expanded) {
            Text(
                text = when {
                    isShell && lineCount <= 1 -> "点击展开查看输出"
                    isShell -> "$lineCount 行输出，点击展开查看"
                    isDiff -> "点击展开查看 diff"
                    else -> "$lineCount 行内容，点击展开查看"
                },
                modifier = Modifier.padding(horizontal = if (compactMode) 9.dp else 10.dp, vertical = if (compactMode) 7.dp else 8.dp),
                color = Color(0xFF9CA3AF),
                fontSize = if (compactMode) 10.sp else 11.sp,
                lineHeight = if (compactMode) 14.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = visibleText,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = if (compactMode) 9.dp else 10.dp, vertical = if (compactMode) 7.dp else 8.dp),
                color = Color(0xFFE5E7EB),
                fontSize = if (compactMode) 10.sp else 11.sp,
                lineHeight = if (compactMode) 15.sp else 16.sp,
                maxLines = if (shouldCollapse && !expanded) 8 else Int.MAX_VALUE,
                overflow = if (shouldCollapse && !expanded) TextOverflow.Ellipsis else TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun CommandOutputBlock(
    messageId: String,
    blockIndex: Int,
    language: String,
    value: String,
    compactMode: Boolean
) {
    CodeBlock(
        messageId = messageId,
        blockIndex = blockIndex,
        language = language,
        value = value,
        compactMode = compactMode
    )
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
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        AnimatedVisibility(visible = state.showComposerDetails) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(top = 3.dp, bottom = 3.dp)
            ) {
                Divider(color = CodexTheme.colors.border, thickness = 1.dp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MiniAction("清空", Icons.Filled.Delete) {
                        onActivePanelChange("none")
                        slashQuery = ""
                        suppressInlineSlashPanel = false
                        onClearComposer()
                    }
                    MiniAction("/命令", Icons.Filled.Search) {
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
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ComposerIconButton(
                onClick = onToggleDetails,
                contentDescription = if (state.showComposerDetails) "收起输入工具" else "展开输入工具",
                size = 32.dp,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (state.showComposerDetails) Icons.Filled.KeyboardArrowDown else Icons.Filled.Add,
                    contentDescription = null,
                    tint = CodexTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 34.dp)
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
                        fontSize = if (compactMode) 12.sp else 13.sp,
                        lineHeight = if (compactMode) 16.sp else 18.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 34.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (composerFieldValue.text.isEmpty()) {
                                Text(
                                    when {
                                        !composerEnabled -> "正在切换会话…"
                                        state.connectionStatus == ConnectionStatus.CONNECTED -> "回复 Codex"
                                        state.connectionStatus == ConnectionStatus.CONNECTING -> "正在连接…"
                                        else -> "未连接"
                                    },
                                    color = CodexTheme.colors.textTertiary,
                                    fontSize = if (compactMode) 12.sp else 13.sp,
                                    lineHeight = if (compactMode) 16.sp else 18.sp
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            if (state.isGenerating) {
                ComposerIconButton(
                    onClick = onStop,
                    contentDescription = "停止生成",
                    size = 32.dp,
                    shape = CircleShape,
                    fill = CodexTheme.colors.danger
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else {
                ComposerIconButton(
                    onClick = {
                        if (sendEnabled) sendNow()
                    },
                    contentDescription = "发送消息",
                    size = 32.dp,
                    shape = CircleShape,
                    fill = when {
                        sendEnabled -> Color(0xFF111827)
                        else -> CodexTheme.colors.surfaceSubtle
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (!sendEnabled) {
                            CodexTheme.colors.textTertiary
                        } else {
                            Color.White
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
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
            is MessageBlock.CommandSummary -> block.value.length
            is MessageBlock.CommandMeta -> block.value.length
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
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = CodexTheme.colors.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = CodexTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isBlank()) {
                            Text("搜索命令", color = CodexTheme.colors.textTertiary, fontSize = 13.sp, lineHeight = 17.sp)
                        }
                        innerTextField()
                    }
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
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = command,
            color = CodexTheme.colors.textPrimary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (supporting.isNotBlank()) {
            Text(
                text = supporting,
                color = CodexTheme.colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniAction(
    label: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CodexTheme.colors.surfaceSubtle)
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            .clearAndSetSemantics { contentDescription = label }
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CodexTheme.colors.textSecondary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = CodexTheme.colors.textPrimary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

private enum class MarkdownLineKind {
    PARAGRAPH,
    HEADING,
    LIST_ITEM,
    QUOTE,
    CODE,
    EMPTY
}

private data class MarkdownLine(
    val kind: MarkdownLineKind,
    val text: String,
    val indent: Int = 0
)

private fun parseMarkdownLines(text: String): List<MarkdownLine> {
    val normalized = text.replace("\r\n", "\n").trimEnd()
    if (normalized.isBlank()) {
        return listOf(MarkdownLine(MarkdownLineKind.EMPTY, ""))
    }
    val lines = normalized.lines()
    val result = mutableListOf<MarkdownLine>()
    var inCodeBlock = false
    lines.forEach { rawLine ->
        val line = rawLine.trimEnd()
        if (line.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            result += MarkdownLine(MarkdownLineKind.CODE, line, 0)
            return@forEach
        }
        if (inCodeBlock) {
            result += MarkdownLine(MarkdownLineKind.CODE, line, 0)
            return@forEach
        }
        when {
            line.isBlank() -> result += MarkdownLine(MarkdownLineKind.EMPTY, "")
            line.startsWith("#") -> result += MarkdownLine(
                MarkdownLineKind.HEADING,
                line.trimStart('#').trim(),
                line.takeWhile { it == '#' }.length
            )
            line.startsWith("- ") || line.startsWith("* ") -> result += MarkdownLine(
                MarkdownLineKind.LIST_ITEM,
                line.drop(2).trim(),
                0
            )
            line.startsWith("> ") -> result += MarkdownLine(
                MarkdownLineKind.QUOTE,
                line.drop(2).trim(),
                0
            )
            else -> result += MarkdownLine(MarkdownLineKind.PARAGRAPH, line, 0)
        }
    }
    return result
}

@Composable
private fun MarkdownText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    maxCollapsedLines: Int,
    wrapContent: Boolean = false
) {
    val displayText = text.trimEnd()
    val lines = remember(displayText) { parseMarkdownLines(displayText) }
    val shouldCollapse = lines.count { it.kind != MarkdownLineKind.EMPTY } > maxCollapsedLines || displayText.length > 180
    val visibleLines = if (shouldCollapse && !expanded) lines.take(maxCollapsedLines) else lines
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        visibleLines.forEach { line ->
            MarkdownLineItem(
                line = line,
                textColor = textColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                wrapContent = wrapContent
            )
        }
        if (shouldCollapse) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onToggle)
                    .clearAndSetSemantics { contentDescription = if (expanded) "收起全文" else "展开全文" }
                    .defaultMinSize(minHeight = 32.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    color = CodexTheme.colors.textTertiary,
                    fontSize = 11.sp
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CodexTheme.colors.textTertiary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryLoadHint(
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = loading,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(180))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("load_older_hint")
                .semantics { contentDescription = "加载更早消息" }
                .padding(top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 1.8.dp,
                modifier = Modifier.size(12.dp),
                color = CodexTheme.colors.textTertiary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "加载更早消息",
                color = CodexTheme.colors.textTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MarkdownLineItem(
    line: MarkdownLine,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
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
            Box(
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

private fun renderInlineMarkdown(text: String): AnnotatedString {
    val source = text.trimEnd()
    if (source.isEmpty()) return AnnotatedString("")
    val builder = buildAnnotatedString {
        var index = 0
        while (index < source.length) {
            val codeStart = source.indexOf('`', index)
            val boldStart = source.indexOf("**", index)
            val italicStart = source.indexOf('*', index)
            val linkStart = source.indexOf("[", index)
            val next = listOf(codeStart, boldStart, italicStart, linkStart).filter { it >= 0 }.minOrNull() ?: source.length
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
    return builder
}

@Composable
private fun MarkdownInlineText(
    text: String,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    val annotatedText = remember(text) { renderInlineMarkdown(text) }
    Text(
        text = annotatedText,
        color = textColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier,
    )
}

@Composable
private fun ComposerIconButton(
    onClick: () -> Unit,
    contentDescription: String,
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
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
