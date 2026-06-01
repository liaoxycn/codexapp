package com.codex.mobile.data

import com.codex.mobile.data.gateway.GatewayCommandSender
import kotlinx.serialization.json.Json
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
    fun senderReturnsTransportResult() {
        val sender = GatewayCommandSender(json) { false }

        val sent = sender.refreshThreads()

        assertFalse(sent)
    }
}
