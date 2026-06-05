package com.codexapp.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.codexapp.ui.state.HomeViewModel
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AgentDebugBridge private constructor(
    private val context: Context,
    private val viewModel: HomeViewModel,
    private val port: Int
) {
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverThread = null
    }

    private fun start(): AgentDebugBridge {
        if (!running.compareAndSet(false, true)) return this
        serverThread = Thread({
            runServer()
        }, "CodexAgentDebugBridge").apply {
            isDaemon = true
            start()
        }
        return this
    }

    private fun runServer() {
        try {
            ServerSocket().use { socket ->
                serverSocket = socket
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName(LOCALHOST), port))
                Log.i(TAG, "Agent debug bridge listening on http://$LOCALHOST:$port")
                while (running.get()) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: continue
                    Thread({
                        client.use { handleClient(it) }
                    }, "CodexAgentDebugClient").apply {
                        isDaemon = true
                        start()
                    }
                }
            }
        } catch (error: Throwable) {
            if (running.get()) {
                Log.w(TAG, "Agent debug bridge stopped after error", error)
            }
        } finally {
            running.set(false)
        }
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 3_000
        val input = socket.getInputStream()
        val requestLine = input.readHttpLine()
        val parts = requestLine.split(" ")
        if (parts.size < 2) {
            socket.writeResponse(400, jsonError("bad_request", "Missing request line"))
            return
        }
        val method = parts[0].uppercase(Locale.US)
        val rawTarget = parts[1]
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readHttpLine()
            if (line.isEmpty()) break
            val split = line.indexOf(':')
            if (split > 0) {
                headers[line.substring(0, split).trim().lowercase(Locale.US)] = line.substring(split + 1).trim()
            }
        }
        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val body = if (contentLength > 0) {
            input.readBody(contentLength)
        } else {
            ""
        }

        val target = parseTarget(rawTarget)
        val response = when {
            method == "GET" && target.path == "/health" -> healthJson()
            method == "GET" && target.path == "/routes" -> routesJson()
            method == "GET" && target.path == "/state" -> viewModel.state.value.toAgentDebugJson(
                messageLimit = target.query["messageLimit"]?.toIntOrNull() ?: 40,
                threadLimit = target.query["threadLimit"]?.toIntOrNull() ?: 50
            )
            method == "POST" && target.path == "/action" -> dispatchAction(body)
            else -> jsonError("not_found", "Unknown endpoint: $method ${target.path}")
        }
        val status = if (response["error"] != null) 404 else 200
        socket.writeResponse(status, response)
    }

    private fun dispatchAction(body: String): JsonObject {
        val payload = runCatching {
            if (body.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(body).jsonObject
        }.getOrElse { error ->
            return jsonError("bad_json", error.message.orEmpty())
        }
        val action = payload.string("action").ifBlank { payload.string("type") }
        if (action.isBlank()) {
            return jsonError("missing_action", "POST /action requires {\"action\":\"...\"}")
        }
        val known = dispatchOnMain(action, payload)
        return if (known) {
            buildJsonObject {
                put("ok", true)
                put("action", action)
                put("dispatched", true)
                put("state", viewModel.state.value.toAgentDebugJson(messageLimit = 12, threadLimit = 20))
            }
        } else {
            jsonError("unknown_action", "Unknown action: $action")
        }
    }

    private fun dispatchOnMain(action: String, payload: JsonObject): Boolean {
        val normalized = action.trim().lowercase(Locale.US)
        val accepted = AtomicBoolean(true)
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                when (normalized) {
                    "refresh_threads" -> viewModel.refreshThreadsAnimated()
                    "refresh_current" -> viewModel.refreshCurrentThreadAnimated()
                    "stop" -> viewModel.stopGenerating()
                    "approve" -> viewModel.approvePending()
                    "reject" -> viewModel.rejectPending()
                    "toggle_composer_details" -> viewModel.toggleComposerDetails()
                    "close_composer_details" -> viewModel.closeComposerDetails()
                    "clear_composer" -> viewModel.clearComposer()
                    "insert_shell_template" -> viewModel.insertShellTemplate()
                    "insert_composer_text" -> viewModel.insertComposerText(payload.string("text"))
                    "apply_slash_command" -> viewModel.applySlashCommand(payload.string("command").ifBlank { payload.string("text") })
                    "compact_context" -> viewModel.compactContext()
                    "rollback_last_turn" -> viewModel.rollbackLastTurn()
                    "set_composer_text" -> viewModel.replaceComposer(payload.string("text"))
                    "edit_and_resend_user_message" -> viewModel.editAndResendUserMessage(
                        payload.string("text"),
                        payload.int("rollbackNumTurns")
                    )
                    "resend_user_message" -> viewModel.resendUserMessage(
                        payload.string("text"),
                        payload.int("rollbackNumTurns")
                    )
                    "send" -> viewModel.send()
                    "send_text" -> {
                        viewModel.replaceComposer(payload.string("text"))
                        viewModel.send()
                    }
                    "new_thread" -> viewModel.createThread(payload.string("cwd").ifBlank { null })
                    "select_thread" -> viewModel.selectThread(payload.string("threadId"))
                    "load_older_messages" -> viewModel.loadOlderMessages()
                    "fork_thread" -> viewModel.forkThread(payload.string("threadId"), payload.optionalInt("numTurns"))
                    "rename_thread" -> viewModel.renameThread(payload.string("threadId"), payload.string("name"))
                    "rename_current_thread" -> viewModel.renameThread(viewModel.state.value.selectedThreadId, payload.string("name"))
                    "archive_thread" -> viewModel.archiveThread(payload.string("threadId"))
                    "archive_current_thread" -> viewModel.archiveThread(viewModel.state.value.selectedThreadId)
                    "unarchive_thread" -> viewModel.unarchiveThread(payload.string("threadId"))
                    "update_new_thread_draft" -> viewModel.updateNewThreadDraft { draft ->
                        draft.copy(
                            cwd = payload.string("cwd").ifBlank { draft.cwd },
                            model = payload.string("model").ifBlank { draft.model },
                            reasoningEffort = payload.string("reasoningEffort").ifBlank { draft.reasoningEffort },
                            permissionMode = payload.string("permissionMode").ifBlank { draft.permissionMode }
                        )
                    }
                    "update_current_thread_config" -> viewModel.updateCurrentThreadConfig { draft ->
                        draft.copy(
                            cwd = payload.string("cwd").ifBlank { draft.cwd },
                            model = payload.string("model").ifBlank { draft.model },
                            reasoningEffort = payload.string("reasoningEffort").ifBlank { draft.reasoningEffort },
                            permissionMode = payload.string("permissionMode").ifBlank { draft.permissionMode }
                        )
                    }
                    "connect_gateway" -> viewModel.connect(payload.string("url"), payload.string("pairToken"))
                    "reconnect_gateway" -> {
                        val config = viewModel.state.value.gatewayConfig
                        viewModel.connect(config.url, config.pairToken)
                    }
                    "disconnect_gateway" -> viewModel.disconnect()
                    else -> accepted.set(false)
                }
            } finally {
                latch.countDown()
            }
        }
        latch.await(2, TimeUnit.SECONDS)
        return accepted.get()
    }

    private fun healthJson(): JsonObject {
        return buildJsonObject {
            put("ok", true)
            put("schema", "codexapp.agentDebug.v1")
            put("package", context.packageName)
            put("port", port)
            put("debuggable", true)
            put("stateEndpoint", "/state")
            put("actionEndpoint", "/action")
        }
    }

    private fun routesJson(): JsonObject {
        return buildJsonObject {
            put("ok", true)
            put("endpoints", buildJsonArray {
                add(JsonPrimitive("GET /health"))
                add(JsonPrimitive("GET /state?messageLimit=40&threadLimit=50"))
                add(JsonPrimitive("GET /routes"))
                add(JsonPrimitive("POST /action"))
            })
            put("actions", buildJsonArray {
                listOf(
                    "refresh_threads",
                    "refresh_current",
                    "stop",
                    "approve",
                    "reject",
                    "toggle_composer_details",
                    "close_composer_details",
                    "clear_composer",
                    "insert_shell_template",
                    "insert_composer_text",
                    "apply_slash_command",
                    "compact_context",
                    "rollback_last_turn",
                    "set_composer_text",
                    "edit_and_resend_user_message",
                    "resend_user_message",
                    "send",
                    "send_text",
                    "new_thread",
                    "select_thread",
                    "load_older_messages",
                    "fork_thread",
                    "rename_thread",
                    "rename_current_thread",
                    "archive_thread",
                    "archive_current_thread",
                    "unarchive_thread",
                    "update_new_thread_draft",
                    "update_current_thread_config",
                    "connect_gateway",
                    "reconnect_gateway",
                    "disconnect_gateway"
                ).forEach { add(JsonPrimitive(it)) }
            })
        }
    }

    private fun InputStream.readHttpLine(): String {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val next = read()
            if (next < 0) break
            if (next == '\n'.code) break
            if (next != '\r'.code) {
                bytes += next.toByte()
            }
        }
        return bytes.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private fun InputStream.readBody(contentLength: Int): String {
        val bytes = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = read(bytes, offset, contentLength - offset)
            if (read < 0) break
            offset += read
        }
        return bytes.copyOf(offset).toString(StandardCharsets.UTF_8)
    }

    private fun Socket.writeResponse(status: Int, payload: JsonObject) {
        val statusText = if (status in 200..299) "OK" else "Error"
        val bytes = json.encodeToString(JsonObject.serializer(), payload).toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        getOutputStream().apply {
            write(header)
            write(bytes)
            flush()
        }
    }

    private data class Target(
        val path: String,
        val query: Map<String, String>
    )

    companion object {
        private const val TAG = "CodexAgentDebug"
        private const val LOCALHOST = "127.0.0.1"
        const val DEFAULT_PORT = 19090

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
            prettyPrint = false
        }

        fun startIfDebuggable(
            context: Context,
            viewModel: HomeViewModel,
            port: Int = DEFAULT_PORT
        ): AgentDebugBridge? {
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (!debuggable) return null
            return AgentDebugBridge(context.applicationContext, viewModel, port).start()
        }

        private fun parseTarget(rawTarget: String): Target {
            val queryStart = rawTarget.indexOf('?')
            val path = if (queryStart >= 0) rawTarget.substring(0, queryStart) else rawTarget
            val rawQuery = if (queryStart >= 0) rawTarget.substring(queryStart + 1) else ""
            return Target(
                path = path,
                query = rawQuery.split('&')
                    .filter { it.isNotBlank() }
                    .associate { part ->
                        val split = part.indexOf('=')
                        val key = if (split >= 0) part.substring(0, split) else part
                        val value = if (split >= 0) part.substring(split + 1) else ""
                        decode(key) to decode(value)
                    }
            )
        }

        private fun decode(value: String): String {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }

        private fun JsonObject.string(key: String): String {
            return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        }

        private fun JsonObject.int(key: String): Int {
            return this[key]?.jsonPrimitive?.intOrNull ?: 0
        }

        private fun JsonObject.optionalInt(key: String): Int? {
            return this[key]?.jsonPrimitive?.intOrNull
        }

        @Suppress("unused")
        private fun JsonObject.boolean(key: String): Boolean {
            return this[key]?.jsonPrimitive?.booleanOrNull == true
        }

        private fun jsonError(code: String, message: String): JsonObject {
            return buildJsonObject {
                put("ok", false)
                put("error", code)
                put("message", message)
            }
        }
    }
}
