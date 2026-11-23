package com.imagine.cast

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

/** What the player feels. The watch always starts in IDLE. */
enum class WatchState { IDLE, ARMED }

class MainActivity : ComponentActivity() {

    // Compose UI state, owned here.
    private var watchState by mutableStateOf(WatchState.IDLE)
    private var relayStatus by mutableStateOf("relay off")
    private var networkType by mutableStateOf("no network")

    private lateinit var connectivity: ConnectivityManager

    // Keeps the status line's network type current, e.g. when Bluetooth is turned off for the Wi-Fi run.
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
        setContent {
            MaterialTheme {
                CastScreen(watchState, "$relayStatus · $networkType")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Screen stays on only while the Activity is resumed. Battery is a named risk.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        networkType = currentNetworkType()
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onPause() {
        connectivity.unregisterNetworkCallback(networkCallback)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
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
}

@Composable
fun CastScreen(state: WatchState, status: String) {
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
        Text(
            text = status,
            color = Color(0xFF9E9E9E),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp),
        )
    }
}
