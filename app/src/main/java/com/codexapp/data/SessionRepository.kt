package com.codexapp.data

import com.codexapp.model.SessionRemoteState
import com.codexapp.model.GatewayConfig
import com.codexapp.model.NewThreadDraft
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val state: StateFlow<SessionRemoteState>

    suspend fun connect(config: GatewayConfig)
    suspend fun disconnect()
    suspend fun createThread(cwd: String? = null, draft: NewThreadDraft? = null)
    suspend fun selectThread(id: String)
    suspend fun forkThread(id: String, numTurns: Int? = null)
    suspend fun renameThread(id: String, name: String)
    suspend fun archiveThread(id: String)
    suspend fun unarchiveThread(id: String)
    suspend fun refreshThreads()
    suspend fun loadOlderMessages()
    fun markManualRefreshing(refreshing: Boolean)
    suspend fun sendPrompt(prompt: String, newThreadDraft: NewThreadDraft? = null): Boolean
    suspend fun rollbackThread(numTurns: Int): Boolean
    suspend fun resendPrompt(prompt: String, rollbackNumTurns: Int): Boolean
    suspend fun stopTurn()
    suspend fun approvePending()
    suspend fun rejectPending()
    suspend fun restartDesktop()
}
