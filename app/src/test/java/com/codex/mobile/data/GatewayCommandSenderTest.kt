package com.codex.mobile.data

import com.codex.mobile.data.gateway.GatewayCommandSender
import kotlinx.serialization.json.Json
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
    fun threadManagementCommandsEncodeExpectedPayloads() {
        val captured = mutableListOf<String>()
        val sender = GatewayCommandSender(json) {
            captured += it
            true
        }

        sender.renameThread("thread-1", "Renamed")
        sender.forkThread("thread-2")
        sender.archiveThread("thread-3")
        sender.unarchiveThread("thread-4")

        val rename = json.parseToJsonElement(captured[0]).jsonObject
        val fork = json.parseToJsonElement(captured[1]).jsonObject
        val archive = json.parseToJsonElement(captured[2]).jsonObject
        val unarchive = json.parseToJsonElement(captured[3]).jsonObject
        assertEquals("rename_thread", rename.getValue("type").jsonPrimitive.content)
        assertEquals("thread-1", rename.getValue("threadId").jsonPrimitive.content)
        assertEquals("Renamed", rename.getValue("name").jsonPrimitive.content)
        assertEquals("fork_thread", fork.getValue("type").jsonPrimitive.content)
        assertEquals("thread-2", fork.getValue("threadId").jsonPrimitive.content)
        assertEquals("archive_thread", archive.getValue("type").jsonPrimitive.content)
        assertEquals("thread-3", archive.getValue("threadId").jsonPrimitive.content)
        assertEquals("unarchive_thread", unarchive.getValue("type").jsonPrimitive.content)
        assertEquals("thread-4", unarchive.getValue("threadId").jsonPrimitive.content)
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
