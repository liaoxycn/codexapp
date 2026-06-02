package com.codexapp.data

import com.codexapp.model.SessionRemoteState
import com.codexapp.model.GatewayConfig
import com.codexapp.model.NewThreadDraft
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val state: StateFlow<SessionRemoteState>

    suspend fun connect(config: GatewayConfig)
    suspend fun disconnect()
    suspend fun createThread(cwd: String? = null, draft: NewThreadDraft? = null): Boolean
    suspend fun selectThread(id: String): Boolean
    suspend fun forkThread(id: String, numTurns: Int? = null): Boolean
    suspend fun renameThread(id: String, name: String): Boolean
    suspend fun archiveThread(id: String): Boolean
    suspend fun unarchiveThread(id: String): Boolean
    suspend fun refreshThreads(): Boolean
    suspend fun loadOlderMessages(): Boolean
    fun markManualRefreshing(refreshing: Boolean)
    suspend fun sendPrompt(prompt: String, draft: NewThreadDraft? = null, newThread: Boolean = false): Boolean
    suspend fun rollbackThread(numTurns: Int): Boolean
    suspend fun resendPrompt(prompt: String, rollbackNumTurns: Int, draft: NewThreadDraft? = null): Boolean
    suspend fun stopTurn(): Boolean
    suspend fun approvePending(): Boolean
    suspend fun rejectPending(): Boolean
    suspend fun restartDesktop(): Boolean
}
