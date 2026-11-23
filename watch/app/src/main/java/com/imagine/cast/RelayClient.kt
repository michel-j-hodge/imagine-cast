package com.imagine.cast

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Streams /bands/{BAND_ID}/state over Firebase REST (Server-Sent Events) and acks every new seq.
 * Owns the seq rules in PHASE_0_ARCHITECTURE.md section 3.
 *
 * Threading: OkHttp delivers stream events on its own thread. Each event is parsed there and then
 * posted to the main thread, where all of this class's state lives. So [onState] and [onStatus]
 * are always called on the main thread.
 */
class RelayClient(
    private val okHttp: OkHttpClient,
    private val parser: RelayParser,
    private val onState: (power: String, live: Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    // Firebase sends a keep-alive about every 30 s. The stream's read timeout must be longer than
    // that, or a quiet stream would be cut. It still catches a dead connection within this time.
    private val streamFactory = EventSources.createFactory(
        okHttp.newBuilder().readTimeout(STREAM_READ_TIMEOUT_S, TimeUnit.SECONDS).build()
    )

    // ---- Main-thread state ----
    private var running = false
    private var generation = 0            // bumped on every (re)connect and stop; stale events are dropped
    private var eventSource: EventSource? = null
    private var attempt = 0               // index into Config.RECONNECT_BACKOFF_MS; reset after a successful open

    // lastSeq starts at 0 with this client. MainActivity creates one client per Activity, and the
    // Activity always starts in IDLE, so "first snapshot after the app starts" is the first
    // snapshot this instance receives.
    private var lastSeq = 0L
    private var firstSnapshotDone = false
    private var awaitingSnapshot = false  // true until the first put on "/" of the current connection

    private val reconnectRunnable = Runnable { connect() }

    fun start() {
        if (running) return
        running = true
        attempt = 0
        connect()
    }

    fun stop() {
        running = false
        generation++
        main.removeCallbacks(reconnectRunnable)
        eventSource?.cancel()
        eventSource = null
        onStatus("off")
    }

    private fun connect() {
        if (!running) return
        val gen = ++generation
        awaitingSnapshot = true
        onStatus(if (attempt == 0) "connecting" else "reconnecting")
        // OkHttp follows Firebase's 307 redirect by default. Do not turn redirects off.
        val request = Request.Builder().url(Config.STATE_URL).build()
        eventSource = streamFactory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                onMain(gen) {
                    attempt = 0
                    onStatus("live")
                }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val event = parser.parse(type, data)
                onMain(gen) { handle(event) }
            }

            override fun onClosed(eventSource: EventSource) {
                onMain(gen) { scheduleReconnect("stream closed") }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val why = t?.message ?: response?.let { "HTTP ${it.code}" } ?: "unknown"
                onMain(gen) { scheduleReconnect("failed: $why") }
            }
        })
    }

    private fun handle(event: RelayEvent?) {
        when (event) {
            null -> Unit // keep-alive
            is RelayEvent.Ignored -> Log.d(TAG, "ignored: ${event.reason}")
            is RelayEvent.Cancel -> scheduleReconnect(event.reason)
            RelayEvent.Empty -> {
                // Nothing written yet. Treat as no state.
                if (awaitingSnapshot) {
                    awaitingSnapshot = false
                    firstSnapshotDone = true
                } else {
                    Log.d(TAG, "state became null, ignored")
                }
            }
            is RelayEvent.State -> onStateEvent(event)
        }
    }

    private fun onStateEvent(s: RelayEvent.State) {
        if (awaitingSnapshot) {
            awaitingSnapshot = false
            if (!firstSnapshotDone) {
                // First snapshot after the app starts: remember its seq only. No state change, no ack.
                // This stops an old "fire" from earlier in the day arming the watch.
                firstSnapshotDone = true
                lastSeq = s.seq
                Log.d(TAG, "first snapshot seq=${s.seq} power=${s.power}, not applied")
                return
            }
            // Snapshot after a reconnect: a newer seq was missed while disconnected.
            // Ack it, then apply silently (no buzz, no sound).
            if (s.seq > lastSeq) {
                lastSeq = s.seq
                sendAck(s.seq)
                onState(s.power, false)
            }
            return
        }
        // Live put: only a newer seq counts. Ack first, then apply.
        if (s.seq > lastSeq) {
            lastSeq = s.seq
            sendAck(s.seq)
            onState(s.power, true)
        } else {
            Log.d(TAG, "old seq ${s.seq} <= $lastSeq, ignored")
        }
    }

    private fun sendAck(seq: Long) {
        val body = JSONObject()
            .put("seq", seq)
            .put("rxAt", System.currentTimeMillis())
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder().url(Config.ACK_URL).put(body).build()
        okHttp.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "ack $seq failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { if (!it.isSuccessful) Log.w(TAG, "ack $seq HTTP ${it.code}") }
            }
        })
    }

    private fun scheduleReconnect(reason: String) {
        if (!running) return
        generation++ // drop anything still arriving from the old stream
        eventSource?.cancel()
        eventSource = null
        main.removeCallbacks(reconnectRunnable)
        val backoff = Config.RECONNECT_BACKOFF_MS
        val delay = backoff[minOf(attempt, backoff.size - 1)]
        attempt++
        Log.i(TAG, "reconnect in $delay ms ($reason)")
        onStatus("reconnecting in ${"%.1f".format(delay / 1000.0)} s")
        main.postDelayed(reconnectRunnable, delay)
    }

    /** Run [block] on the main thread, unless the stream it came from has since been replaced or stopped. */
    private fun onMain(gen: Int, block: () -> Unit) {
        main.post { if (running && gen == generation) block() }
    }

    private companion object {
        const val TAG = "RelayClient"
        const val STREAM_READ_TIMEOUT_S = 45L
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
