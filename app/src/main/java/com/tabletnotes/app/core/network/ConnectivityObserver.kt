package com.tabletnotes.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
    val isSimulatedOffline: StateFlow<Boolean>
    fun setSimulatedOffline(offline: Boolean)
}

@Singleton
class AndroidConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context
) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _systemOnline = MutableStateFlow(checkCurrentConnectivity())
    private val _isSimulatedOffline = MutableStateFlow(false)
    override val isSimulatedOffline: StateFlow<Boolean> = _isSimulatedOffline.asStateFlow()

    private val _isOnline = MutableStateFlow(_systemOnline.value && !_isSimulatedOffline.value)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _systemOnline.value = true
                        refreshEffectiveOnline()
                    }

                    override fun onLost(network: Network) {
                        _systemOnline.value = checkCurrentConnectivity()
                        refreshEffectiveOnline()
                    }
                }
            )
        }
    }

    override fun setSimulatedOffline(offline: Boolean) {
        _isSimulatedOffline.value = offline
        refreshEffectiveOnline()
    }

    private fun refreshEffectiveOnline() {
        _isOnline.value = _systemOnline.value && !_isSimulatedOffline.value
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

