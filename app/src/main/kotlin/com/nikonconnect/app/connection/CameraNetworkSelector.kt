package com.nikonconnect.app.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiNetworkSpecifier
import android.os.PatternMatcher
import com.nikonconnect.ptp.NikonSsidPolicy
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class SelectedNetworkLease internal constructor(
    val network: Network,
    val ssid: String,
    private val connectivityManager: ConnectivityManager,
    private val callback: ConnectivityManager.NetworkCallback,
) : AutoCloseable {
    private val released = AtomicBoolean(false)

    override fun close() {
        if (released.compareAndSet(false, true)) {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }
}

class CameraNetworkSelector(context: Context) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    suspend fun requestNikonNetwork(): Result<SelectedNetworkLease> =
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)
            lateinit var callback: ConnectivityManager.NetworkCallback

            fun fail(message: String) {
                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                    continuation.resume(Result.failure(IllegalStateException(message)))
                }
            }

            callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!resumed.compareAndSet(false, true) || !continuation.isActive) return

                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    val wifiInfo = capabilities?.transportInfo as? WifiInfo
                    val reportedSsid = wifiInfo?.ssid
                    val verifiedSsid = NikonSsidPolicy.normalize(reportedSsid)

                    if (verifiedSsid != null && !NikonSsidPolicy.isNikon(verifiedSsid)) {
                        runCatching { connectivityManager.unregisterNetworkCallback(this) }
                        continuation.resume(
                            Result.failure(IllegalStateException("所选 Wi‑Fi 名称不是以 NIKON 开头。")),
                        )
                        return
                    }

                    continuation.resume(
                        Result.success(
                            SelectedNetworkLease(
                                network = network,
                                ssid = verifiedSsid ?: "NIKON（系统已按前缀筛选）",
                                connectivityManager = connectivityManager,
                                callback = this,
                            ),
                        ),
                    )
                }

                override fun onUnavailable() {
                    fail("未选择 Nikon 相机 Wi‑Fi，或系统未找到以 NIKON 开头的网络。")
                }

                override fun onLost(network: Network) {
                    // The session observes socket closure. Keeping this callback small avoids duplicate state owners.
                }
            }

            val specifier = WifiNetworkSpecifier.Builder()
                .setSsidPattern(PatternMatcher("NIKON", PatternMatcher.PATTERN_PREFIX))
                .build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            continuation.invokeOnCancellation {
                if (resumed.compareAndSet(false, true)) {
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                }
            }
            connectivityManager.requestNetwork(request, callback)
        }
}
