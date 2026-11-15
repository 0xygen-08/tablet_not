package com.tabletnotes.app.feature.nearby.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 8.0 (API 26) ile Android 15+ (API 35) arasındaki tüm sürümler için
 * Google Nearby Connections (Bluetooth + Wi-Fi Direct) çalışma zamanı izinlerini
 * doğru şekilde belirleyen yardımcı sınıf.
 */
object NearbyPermissionsHelper {

    fun getRequiredPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (sdkInt >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+): Yakındaki Bluetooth cihazları tarama, görünür olma ve bağlanma
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+): Yüksek hızlı Wi-Fi Direct (Nearby Wi-Fi Devices) aktarımı
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }

        return permissions.distinct()
    }

    fun hasAllPermissions(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return getRequiredPermissions(sdkInt).all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}

