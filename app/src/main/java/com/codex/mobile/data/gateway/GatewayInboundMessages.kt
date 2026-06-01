package com.codex.mobile.data.gateway

import kotlinx.serialization.json.Json

internal sealed interface GatewayInboundMessage {
    data class Snapshot(val payload: GatewaySnapshotMessage) : GatewayInboundMessage
    data class Status(val payload: GatewayStatusMessage) : GatewayInboundMessage
}

internal fun decodeGatewayInboundMessage(
    json: Json,
    raw: String
): GatewayInboundMessage {
    return when (json.decodeFromString(GatewayEnvelope.serializer(), raw).type) {
        "snapshot" -> GatewayInboundMessage.Snapshot(
            json.decodeFromString(GatewaySnapshotMessage.serializer(), raw)
        )

        "status" -> GatewayInboundMessage.Status(
            json.decodeFromString(GatewayStatusMessage.serializer(), raw)
        )

        else -> error("unsupported gateway message type")
    }
}
