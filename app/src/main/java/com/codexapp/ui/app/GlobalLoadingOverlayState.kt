package com.codexapp.ui.app

import com.codexapp.model.HomeUiState

internal fun resolveGlobalLoadingText(state: HomeUiState): String? {
    if (state.isForkingThread) return "正在生成分叉会话"
    if (!state.pendingSelectionThreadId.isNullOrBlank()) return "正在切换会话"

    val actionType = state.diagnostics.actionType
    if (state.diagnostics.actionStatus != "started") {
        return when {
            state.isManualRefreshing -> "正在刷新中"
            state.isLoadingOlder -> "正在加载历史中"
            else -> null
        }
    }

    return when (actionType) {
        "hello/select_thread", "select_thread" -> "正在切换会话"
        "archive_thread" -> "正在归档中"
        "unarchive_thread" -> "正在恢复中"
        "rename_thread" -> "正在重命名中"
        "refresh_threads" -> "正在刷新中"
        "load_older_messages" -> "正在加载历史中"
        "create_thread" -> "正在新建会话中"
        "fork_thread" -> "正在生成分叉会话"
        "send_prompt" -> "正在发送中"
        "rollback_thread" -> "正在回滚中"
        "resend_prompt" -> "正在重发中"
        "stop_turn" -> "正在停止中"
        "approve_pending" -> "正在审批中"
        "reject_pending" -> "正在拒绝中"
        "restart_desktop" -> "正在重启桌面端"
        else -> null
    }
}
