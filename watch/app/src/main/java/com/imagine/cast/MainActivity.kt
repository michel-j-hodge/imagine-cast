package com.imagine.cast

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/** What the player feels. The watch always starts in IDLE. */
enum class WatchState { IDLE, ARMED }

/** Watch-side events. ARM_FIRE and DISARM come from the relay; FIREBALL comes from a local punch. */
enum class WatchEvent { ARM_FIRE, DISARM, FIREBALL }

class MainActivity : ComponentActivity(), SensorEventListener {

    // Compose UI state, owned here.
    private var watchState by mutableStateOf(WatchState.IDLE)
    private var relayStatus by mutableStateOf("off")
    private var networkType by mutableStateOf("no network")
    /** Bumped on every FIREBALL; each new value plays one flash. */
    private var flashCount by mutableIntStateOf(0)

    private lateinit var connectivity: ConnectivityManager
    private lateinit var feedback: FeedbackManager
    private lateinit var relayClient: RelayClient
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val punchDetector = PunchDetector()

    // Diagnostic only (Open Question: false positives). Tracks each spike above SPIKE_LOG_MIN and logs
    // its peak at the end, so a threshold can be chosen from measured data. Does not affect firing.
    private var spikeStartMs = -1L
    private var spikePeak = 0f
    private var spikeFired = false
    private var lastFireballMs = -1L

    // Keeps the network line current, e.g. when Bluetooth is turned off for the Wi-Fi run.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val type = describe(caps)
            runOnUiThread { networkType = type }
        }

        override fun onLost(network: Network) {
            runOnUiThread { networkType = currentNetworkType() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivity = getSystemService(ConnectivityManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) Log.e(TAG, "no accelerometer: punches cannot fire")
        // Dependencies are wired by hand.
        feedback = FeedbackManager(applicationContext)
        relayClient = RelayClient(
            okHttp = OkHttpClient(),
            parser = RelayParser(),
            onState = ::onRelayState,
            onStatus = { relayStatus = it },
        )
        setContent {
            MaterialTheme {
                CastScreen(watchState, relayStatus, networkType, flashCount)
            }
        }
    }

    /** Called on the main thread by RelayClient, after the ack has been sent. */
    private fun onRelayState(power: String, live: Boolean) {
        when (power) {
            "fire" -> onEvent(WatchEvent.ARM_FIRE, live)
            "none" -> onEvent(WatchEvent.DISARM, live)
            else -> Log.w(TAG, "unknown power \"$power\", ignored")
        }
    }

    /** [live] is false for a state that was missed while disconnected: apply it silently. */
    private fun onEvent(event: WatchEvent, live: Boolean) {
        when (event) {
            WatchEvent.ARM_FIRE -> {
                // Arming while already ARMED buzzes again: that confirms the cast landed.
                watchState = WatchState.ARMED
                if (live) feedback.armed()
            }
            WatchEvent.DISARM -> watchState = WatchState.IDLE
            WatchEvent.FIREBALL -> {
                // Punch detection and feedback are local to the watch: no network delay on firing.
                feedback.fireball()
                flashCount++
            }
        }
    }

    // Accelerometer callbacks arrive on the main thread (registered without a Handler).
    override fun onSensorChanged(event: SensorEvent) {
        // Raw TYPE_ACCELEROMETER values include gravity; PunchDetector's threshold is set with that in mind.
        // event.timestamp is monotonic nanoseconds, so it is safe for the cooldown.
        val (x, y, z) = event.values
        val tMs = event.timestamp / 1_000_000L
        val punch = punchDetector.onSample(x, y, z, tMs)
        if (punch) {
            if (watchState == WatchState.ARMED) {
                onEvent(WatchEvent.FIREBALL, live = true)
                lastFireballMs = tMs
            } else {
                // The sign unlocks the power: a punch in IDLE does nothing. Logged to help read cast-then-punch misses.
                Log.d(TAG, "punch while IDLE, ignored")
            }
        }
        logSpike(sqrt(x * x + y * y + z * z), tMs, punch)
    }

    /** Diagnostic: one "spike" log line per movement above SPIKE_LOG_MIN (magnitude includes gravity). */
    private fun logSpike(magnitude: Float, tMs: Long, punch: Boolean) {
        if (magnitude > SPIKE_LOG_MIN) {
            if (spikeStartMs < 0) {
                spikeStartMs = tMs
                spikePeak = 0f
                spikeFired = false
            }
            if (magnitude > spikePeak) spikePeak = magnitude
            if (punch) spikeFired = true
            return
        }
        if (spikeStartMs < 0) return
        val sinceFireball = if (lastFireballMs < 0) "-" else "${spikeStartMs - lastFireballMs}"
        Log.d(
            TAG,
            "spike peak=%.1f dur=%dms detected=%b state=%s sinceFireball=%sms".format(
                spikePeak, tMs - spikeStartMs, spikeFired, watchState, sinceFireball,
            ),
        )
        spikeStartMs = -1L
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onResume() {
        super.onResume()
        // Screen stays on only while the Activity is resumed. Battery is a named risk.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        networkType = currentNetworkType()
        connectivity.registerDefaultNetworkCallback(networkCallback)
        relayClient.start()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        relayClient.stop()
        connectivity.unregisterNetworkCallback(networkCallback)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }

    private fun currentNetworkType(): String {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return "no network"
        return describe(caps)
    }

    private fun describe(caps: NetworkCapabilities): String = when {
        // By default a Wear watch reaches the internet through the paired phone over Bluetooth.
        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BT proxy"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "LTE"
        else -> "other"
    }

    private companion object {
        const val TAG = "MainActivity"
        /** Diagnostic logging floor, m/s² including gravity. Below the punch threshold on purpose. */
        const val SPIKE_LOG_MIN = 12f
    }
}

@Composable
fun CastScreen(state: WatchState, relayStatus: String, networkType: String, flashCount: Int) {
    val armed = state == WatchState.ARMED
    // FIREBALL: a short orange-white flash over whichever state is showing.
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flashCount) {
        if (flashCount == 0) return@LaunchedEffect
        flashing = true
        delay(FLASH_MS)
        flashing = false
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (armed) Color(0xFFD50000) else Color(0xFF080808)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (armed) "FIRE" else "Make a sign",
            color = Color.White,
            fontSize = if (armed) 40.sp else 16.sp,
            fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        // Two short centred lines, so neither one runs off the round screen.
        // The network line is last and single-line, so it is always visible (the delay test needs it).
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusLine(relayStatus)
            StatusLine(networkType)
        }
        if (flashing) {
            Box(Modifier.fillMaxSize().background(Color(0xFFFFB74D)))
        }
    }
}

/** How long the FIREBALL flash stays on screen. */
private const val FLASH_MS = 300L

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        color = Color(0xFF9E9E9E),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}
