package com.example.webdavplayer.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络连接状态监控（§1.6 中优项）。
 *
 * 基于 [ConnectivityManager] 回调，提供实时的网络可用性 [StateFlow]。
 * 用于：
 * - 浏览页：离线时显示提示，恢复时自动刷新
 * - 播放页：网络断开时暂停并提示，恢复时可继续播放
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    /**
     * 注册网络回调，开始监听连接变化。
     * 应在 Application.onCreate 或 Service.onCreate 中调用。
     */
    fun startMonitoring() {
        if (callback != null) return

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                _isOnline.value = checkOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                _isOnline.value = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                )
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, cb)
        callback = cb
    }

    /**
     * 注销网络回调，停止监听。
     */
    fun stopMonitoring() {
        callback?.let { connectivityManager.unregisterNetworkCallback(it) }
        callback = null
    }

    /**
     * 以 Flow 形式观察网络状态（适合在 Composable 中 collectAsStateWithLifecycle）。
     */
    fun observe(): Flow<Boolean> = callbackFlow {
        val cm = connectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(checkOnline()) }
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, cb)
        trySend(checkOnline())
        awaitClose { cm.unregisterNetworkCallback(cb) }
    }

    private fun checkOnline(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
