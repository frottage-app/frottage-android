package com.frottage

import android.content.Context
import android.util.Log
import java.util.UUID

object DeviceIdManager {
    private const val TAG = "DeviceIdManager"

    // Use the original SharedPreferences name and key from RatingService for compatibility
    private const val APP_PREFS_NAME = "FrottagePrefs"
    private const val DEVICE_ID_KEY = "myFrottageDeviceId"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(DEVICE_ID_KEY, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply()
            Log.i(TAG, "Groovy! Generated new unique deviceId (key: $DEVICE_ID_KEY) in prefs: $APP_PREFS_NAME: $deviceId")
        } else {
            Log.d(TAG, "Fetched existing unique deviceId (key: $DEVICE_ID_KEY) from prefs: $APP_PREFS_NAME: $deviceId")
        }
        return deviceId
    }
}
