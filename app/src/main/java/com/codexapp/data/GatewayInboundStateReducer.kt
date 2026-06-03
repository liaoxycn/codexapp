package com.codexapp.data

import com.codexapp.data.gateway.GatewayInboundMessage
import com.codexapp.data.gateway.applyTo
import com.codexapp.data.gateway.decodeGatewayInboundMessage
import com.codexapp.data.gateway.isStaleFor
import com.codexapp.model.SessionRemoteState
import kotlinx.serialization.json.Json

internal fun reduceGatewayInboundStateBatch(
    json: Json,
    previous: SessionRemoteState,
    raws: List<String>,
    onSnapshotPatchMismatch: () -> Unit = {}
): SessionRemoteState {
    return raws.fold(previous) { state, raw ->
        reduceGatewayInboundState(
            json = json,
            previous = state,
            raw = raw,
            onSnapshotPatchMismatch = onSnapshotPatchMismatch
        )
    }
}

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
