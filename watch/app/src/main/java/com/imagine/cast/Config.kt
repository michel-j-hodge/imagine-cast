package com.imagine.cast

/**
 * Relay settings. These must match the camera page's Tuning block
 * (docs/index.html) and the relay contract in PHASE_0_ARCHITECTURE.md section 3.
 */
object Config {
    /** Firebase Realtime Database, Singapore region, REST only (no SDK). */
    const val DB_URL = "https://imagine-cast-default-rtdb.asia-southeast1.firebasedatabase.app"

    const val BAND_ID = "band-01"

    /** Streamed by the watch (SSE). Written by the page. */
    const val STATE_URL = "$DB_URL/bands/$BAND_ID/state.json"

    /** Written by the watch (PUT) right after each new seq. Streamed by the page. */
    const val ACK_URL = "$DB_URL/bands/$BAND_ID/ack.json"

    /** Delay before each reconnect attempt. The last value is the cap and repeats. Reset after a successful open. */
    val RECONNECT_BACKOFF_MS: List<Long> = listOf(500L, 1000L, 2000L, 4000L, 5000L)
}
