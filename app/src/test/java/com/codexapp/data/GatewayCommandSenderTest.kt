package com.codexapp.data

import com.codexapp.data.gateway.GatewayCommandSender
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayCommandSenderTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun sendHelloOmitsBlankPairTokenAndKeepsClientMetadata() {
        val captured = mutableListOf<String>()
        val sender = GatewayCommandSender(json) {
            captured += it
            true
        }

        val sent = sender.sendHello("")

        assertTrue(sent)
        val payload = json.parseToJsonElement(captured.single()).jsonObject
        assertEquals("hello", payload.getValue("type").jsonPrimitive.content)
        assertEquals("android-shell", payload.getValue("client").jsonPrimitive.content)
        assertEquals("0.2.0", payload.getValue("version").jsonPrimitive.content)
        assertEquals("snapshot_patch", payload.getValue("capabilities").jsonArray.single().jsonPrimitive.content)
        assertFalse(payload.containsKey("pairToken"))
        assertFalse(payload.containsKey("selectedThreadId"))
    }

    @Test
    fun sendHelloCarriesCurrentSelectedThread() {
        val captured = mutableListOf<String>()
        val sender = GatewayCommandSender(json) {
            captured += it
            true
        }

        val sent = sender.sendHello("pair-token", selectedThreadId = "thread-2")

        assertTrue(sent)
        val payload = json.parseToJsonElement(captured.single()).jsonObject
        assertEquals("hello", payload.getValue("type").jsonPrimitive.content)
        assertEquals("pair-token", payload.getValue("pairToken").jsonPrimitive.content)
        assertEquals("thread-2", payload.getValue("selectedThreadId").jsonPrimitive.content)
    }

    @Test
    fun createThreadAndPromptOmitBlankOptionalFields() {
        val captured = mutableListOf<String>()
        val sender = GatewayCommandSender(json) {
            captured += it
            true
        }

        sender.createThread("   ")
        sender.sendPrompt("hello", null)

        val createThread = json.parseToJsonElement(captured[0]).jsonObject
        val sendPrompt = json.parseToJsonElement(captured[1]).jsonObject
        assertEquals("create_thread", createThread.getValue("type").jsonPrimitive.content)
        assertFalse(createThread.containsKey("cwd"))
        assertEquals("send_prompt", sendPrompt.getValue("type").jsonPrimitive.content)
        assertEquals("hello", sendPrompt.getValue("text").jsonPrimitive.content)
        assertNull(sendPrompt["threadId"])
    }

    @Test
    fun sendPromptCanRequestNewThreadWithDraftOptions() {
        val captured = mutableListOf<String>()
        val sender = GatewayCommandSender(json) {
            captured += it
            true
        }

        sender.sendPrompt(
            text = "hello",
            threadId = null,
            newThread = true,
            cwd = "D:/Projects/App",
            model = "gpt-5",
            reasoningEffort = "high",
            sandboxMode = "workspace-write"
        )

        val payload = json.parseToJsonElement(captured.single()).jsonObject
        assertEquals("send_prompt", payload.getValue("type").jsonPrimitive.content)
        assertEquals(true, payload.getValue("newThread").jsonPrimitive.boolean)
        assertEquals("D:/Projects/App", payload.getValue("cwd").jsonPrimitive.content)
        assertEquals("gpt-5", payload.getValue("model").jsonPrimitive.content)
        assertEquals("high", payload.getValue("reasoningEffort").jsonPrimitive.content)
        assertFalse(payload.containsKey("approvalPolicy"))
        assertEquals("workspace-write", payload.getValue("sandboxMode").jsonPrimitive.content)
    }

    @Test
    fun rollbackAndResendCommandsEncodeTurnScopedPayloads() {
        val captured = mutableListOf<String>()
        val sender = GatewayCommandSender(json) {
            captured += it
            true
        }

        sender.rollbackThread("thread-1", 3)
        sender.resendPrompt("hello again", "thread-1", 2)

        val rollback = json.parseToJsonElement(captured[0]).jsonObject
        val resend = json.parseToJsonElement(captured[1]).jsonObject
        assertEquals("rollback_thread", rollback.getValue("type").jsonPrimitive.content)
        assertEquals("thread-1", rollback.getValue("threadId").jsonPrimitive.content)
        assertEquals("3", rollback.getValue("numTurns").jsonPrimitive.content)
        assertEquals("resend_prompt", resend.getValue("type").jsonPrimitive.content)
        assertEquals("thread-1", resend.getValue("threadId").jsonPrimitive.content)
        assertEquals("hello again", resend.getValue("text").jsonPrimitive.content)
        assertEquals("2", resend.getValue("rollbackNumTurns").jsonPrimitive.content)
    }

    @Test
    fun threadManagementCommandsEncodeExpectedPayloads() {
        val captured = mutableListOf<String>()
        val sender = GatewayCommandSender(json) {
            captured += it
            true
        }

        sender.renameThread("thread-1", "Renamed")
        sender.forkThread("thread-2", 3)
        sender.archiveThread("thread-3")
        sender.unarchiveThread("thread-4")
        sender.restartDesktop()

        val rename = json.parseToJsonElement(captured[0]).jsonObject
        val fork = json.parseToJsonElement(captured[1]).jsonObject
        val archive = json.parseToJsonElement(captured[2]).jsonObject
        val unarchive = json.parseToJsonElement(captured[3]).jsonObject
        val restart = json.parseToJsonElement(captured[4]).jsonObject
        assertEquals("rename_thread", rename.getValue("type").jsonPrimitive.content)
        assertEquals("thread-1", rename.getValue("threadId").jsonPrimitive.content)
        assertEquals("Renamed", rename.getValue("name").jsonPrimitive.content)
        assertEquals("fork_thread", fork.getValue("type").jsonPrimitive.content)
        assertEquals("thread-2", fork.getValue("threadId").jsonPrimitive.content)
        assertEquals("3", fork.getValue("numTurns").jsonPrimitive.content)
        assertEquals("archive_thread", archive.getValue("type").jsonPrimitive.content)
        assertEquals("thread-3", archive.getValue("threadId").jsonPrimitive.content)
        assertEquals("unarchive_thread", unarchive.getValue("type").jsonPrimitive.content)
        assertEquals("thread-4", unarchive.getValue("threadId").jsonPrimitive.content)
        assertEquals("restart_desktop", restart.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun senderReturnsTransportResult() {
        val sender = GatewayCommandSender(json) { false }

        val sent = sender.refreshThreads()

        assertFalse(sent)
    }

    @Test
    fun refreshThreadsCanRequestForcedSnapshot() {
        val captured = mutableListOf<String>()
        val sender = GatewayCommandSender(json) {
            captured += it
            true
        }

        val sent = sender.refreshThreads(forceSnapshot = true)

        assertTrue(sent)
        val payload = json.parseToJsonElement(captured.single()).jsonObject
        assertEquals("refresh_threads", payload.getValue("type").jsonPrimitive.content)
        assertEquals("true", payload.getValue("forceSnapshot").jsonPrimitive.content)
    }
}
