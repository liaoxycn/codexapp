package com.codex.mobile.data

import com.codex.mobile.data.gateway.GatewayInboundMessage
import com.codex.mobile.data.gateway.applyTo
import com.codex.mobile.data.gateway.decodeGatewayInboundMessage
import com.codex.mobile.data.gateway.isStaleFor
import com.codex.mobile.model.SessionRemoteState
import kotlinx.serialization.json.Json

internal fun reduceGatewayInboundState(
    json: Json,
    previous: SessionRemoteState,
    raw: String,
    onSnapshotPatchMismatch: () -> Unit = {}
): SessionRemoteState {
    return runCatching {
        when (val inbound = decodeGatewayInboundMessage(json, raw)) {
            is GatewayInboundMessage.Snapshot -> inbound.payload.applyTo(previous)
            is GatewayInboundMessage.SnapshotPatch -> {
                if (inbound.payload.isStaleFor(previous)) {
                    onSnapshotPatchMismatch()
                }
                inbound.payload.applyTo(previous)
            }
            is GatewayInboundMessage.Status -> inbound.payload.applyTo(previous)
        }
    }.getOrElse { error ->
        previous.withInboundDecodeFailure(error.message)
    }
}
