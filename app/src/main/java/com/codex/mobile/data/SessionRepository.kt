package com.codex.mobile.data

import com.codex.mobile.model.SessionRemoteState
import com.codex.mobile.model.GatewayConfig
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val state: StateFlow<SessionRemoteState>

    suspend fun connect(config: GatewayConfig)
    suspend fun disconnect()
    suspend fun createThread(cwd: String? = null)
    suspend fun selectThread(id: String)
    suspend fun refreshThreads()
    suspend fun loadOlderMessages()
    fun markManualRefreshing(refreshing: Boolean)
    suspend fun sendPrompt(prompt: String): Boolean
    suspend fun stopTurn()
    suspend fun approvePending()
    suspend fun rejectPending()
}
