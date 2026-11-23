package com.imagine.cast

import org.json.JSONObject

/** One parsed Firebase stream event. See PHASE_0_ARCHITECTURE.md section 3. */
sealed class RelayEvent {
    /** `put` on "/" with an object that has `power` and `seq`. */
    data class State(val power: String, val seq: Long) : RelayEvent()

    /** `put` on "/" with null data: nothing has been written yet. Treat as no state. */
    data object Empty : RelayEvent()

    /** `cancel` or `auth_revoked`: close the stream and reconnect with backoff. */
    data class Cancel(val reason: String) : RelayEvent()

    /** Log it, then ignore it: a `patch`, a `put` on a child path, or a payload we can't use. */
    data class Ignored(val reason: String) : RelayEvent()
}

/**
 * Turns one Server-Sent Event from the Firebase REST stream into a [RelayEvent].
 * Pure: no Android dependencies, never throws. Returns null for `keep-alive`.
 */
class RelayParser {

    fun parse(eventType: String?, dataJson: String?): RelayEvent? = when (eventType) {
        "keep-alive" -> null
        "cancel", "auth_revoked" -> RelayEvent.Cancel(eventType)
        "patch" -> RelayEvent.Ignored("patch")
        "put" -> parsePut(dataJson)
        else -> RelayEvent.Ignored("unknown event type: $eventType")
    }

    private fun parsePut(dataJson: String?): RelayEvent {
        val msg = try {
            JSONObject(dataJson ?: return RelayEvent.Ignored("put without data"))
        } catch (e: Exception) {
            return RelayEvent.Ignored("malformed JSON")
        }
        val path = msg.optString("path", "")
        if (path != "/") return RelayEvent.Ignored("put on child path $path")
        if (!msg.has("data") || msg.isNull("data")) return RelayEvent.Empty
        val data = msg.optJSONObject("data") ?: return RelayEvent.Ignored("data is not an object")
        val power = data.opt("power") as? String ?: return RelayEvent.Ignored("missing power")
        val seq = (data.opt("seq") as? Number)?.toLong() ?: return RelayEvent.Ignored("missing seq")
        return RelayEvent.State(power, seq)
    }
}
