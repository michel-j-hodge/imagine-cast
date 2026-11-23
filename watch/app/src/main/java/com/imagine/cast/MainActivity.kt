package com.imagine.cast

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import okhttp3.OkHttpClient

/** What the player feels. The watch always starts in IDLE. */
enum class WatchState { IDLE, ARMED }

/** Watch-side events. ARM_FIRE and DISARM come from the relay; FIREBALL comes from the punch (Task 6). */
enum class WatchEvent { ARM_FIRE, DISARM, FIREBALL }

class MainActivity : ComponentActivity() {

    // Compose UI state, owned here.
    private var watchState by mutableStateOf(WatchState.IDLE)
    private var relayStatus by mutableStateOf("off")
    private var networkType by mutableStateOf("no network")

    private lateinit var connectivity: ConnectivityManager
    private lateinit var feedback: FeedbackManager
    private lateinit var relayClient: RelayClient

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
                CastScreen(watchState, relayStatus, networkType)
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
            WatchEvent.FIREBALL -> Unit // Task 6
        }
    }

    override fun onResume() {
        super.onResume()
        // Screen stays on only while the Activity is resumed. Battery is a named risk.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        networkType = currentNetworkType()
        connectivity.registerDefaultNetworkCallback(networkCallback)
        relayClient.start()
    }

    override fun onPause() {
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
    }
}

@Composable
fun CastScreen(state: WatchState, relayStatus: String, networkType: String) {
    val armed = state == WatchState.ARMED
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
    }
}

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
