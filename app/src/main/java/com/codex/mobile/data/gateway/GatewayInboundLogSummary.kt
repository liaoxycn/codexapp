package com.codex.mobile.data.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun summarizeInboundForLog(json: Json, raw: String): String {
    return runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val type = root.stringValue("type").ifBlank { "unknown" }
        val parts = mutableListOf("type=${type.take(40)}", "bytes=${raw.toByteArray().size}")
        root.longValue("revision")?.let { parts += "revision=$it" }
        when (type) {
            "snapshot" -> {
                parts += "threads=${root.arraySize("threads")}"
                parts += "messages=${root.arraySize("messages")}"
                parts += "selected=${root.stringValue("selectedThreadId").redactedId()}"
                parts += "generating=${root.booleanText("isGenerating")}"
                root.obj("diagnostics")?.let { diagnostics ->
                    parts += "running=${diagnostics.arraySize("runningThreadIds")}"
                    parts += "action=${diagnostics.stringValue("actionType").ifBlank { "-" }}"
                    parts += "actionStatus=${diagnostics.stringValue("actionStatus").ifBlank { "-" }}"
                }
            }
            "snapshot_patch" -> {
                root.longValue("baseRevision")?.let { parts += "base=$it" }
                parts += "changed=${root.stringArray("changed").joinToString("|").ifBlank { "-" }}"
                root.arraySizeOrNull("messages")?.let { parts += "messages=$it" }
                root.arraySizeOrNull("threads")?.let { parts += "threads=$it" }
                root.stringValue("selectedThreadId").takeIf(String::isNotBlank)?.let {
                    parts += "selected=${it.redactedId()}"
                }
            }
            "status" -> {
                parts += "status=${root.stringValue("status").ifBlank { "-" }}"
                parts += "detailLen=${root.stringValue("detail").length}"
            }
        }
        parts.joinToString(" ")
    }.getOrElse {
        "type=unparseable bytes=${raw.toByteArray().size} error=${it::class.simpleName.orEmpty()}"
    }
}

private fun JsonObject.stringValue(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.longValue(key: String): Long? {
    return this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
}

private fun JsonObject.booleanText(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull ?: "false"
}

private fun JsonObject.obj(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.arraySize(key: String): Int {
    return arraySizeOrNull(key) ?: 0
}

private fun JsonObject.arraySizeOrNull(key: String): Int? {
    return (this[key] as? JsonArray)?.size
}

private fun JsonObject.stringArray(key: String): List<String> {
    return (this[key] as? JsonArray)
        ?.mapNotNull(JsonElement::jsonPrimitiveOrNull)
        ?.mapNotNull { it.contentOrNull }
        .orEmpty()
}

private fun JsonElement.jsonPrimitiveOrNull() = runCatching { jsonPrimitive }.getOrNull()

private fun String.redactedId(): String {
    if (isBlank()) return "-"
    return if (length <= 10) this else "${take(6)}...${takeLast(4)}"
}
