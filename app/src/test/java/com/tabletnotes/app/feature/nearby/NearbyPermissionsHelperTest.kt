package com.tabletnotes.app.feature.nearby

import android.Manifest
import android.os.Build
import com.tabletnotes.app.feature.nearby.permissions.NearbyPermissionsHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPermissionsHelperTest {

    @Test
    fun `android 8 to 11 requires fine and coarse location permissions`() {
        val permsApi26 = NearbyPermissionsHelper.getRequiredPermissions(sdkInt = Build.VERSION_CODES.O)
        assertTrue(permsApi26.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permsApi26.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertFalse(permsApi26.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertFalse(permsApi26.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
    }

    @Test
    fun `android 12 requires bluetooth scan advertise and connect permissions`() {
        val permsApi31 = NearbyPermissionsHelper.getRequiredPermissions(sdkInt = Build.VERSION_CODES.S)
        assertTrue(permsApi31.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(permsApi31.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
        assertTrue(permsApi31.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertFalse(permsApi31.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
    }

    @Test
    fun `android 13 and above requires nearby wifi devices and bluetooth permissions`() {
        val permsApi33 = NearbyPermissionsHelper.getRequiredPermissions(sdkInt = Build.VERSION_CODES.TIRAMISU)
        assertTrue(permsApi33.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(permsApi33.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
        assertTrue(permsApi33.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(permsApi33.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
    }
}

