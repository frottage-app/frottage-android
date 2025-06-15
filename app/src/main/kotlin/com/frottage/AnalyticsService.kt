package com.frottage

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

object AnalyticsService {
    private const val TAG = "AnalyticsService"

    private fun getAppVersion(context: Context): String =
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown_version"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Frottage Alert! Could not get package info to determine app version.", e)
            "unknown_version"
        }

    fun trackEvent(
        context: Context,
        eventName: String,
        properties: JsonObject? = null,
    ) {
        Log.i(TAG, "event: '$eventName', Raw Properties: $properties")

        CoroutineScope(Dispatchers.IO).launch {
            val deviceId: String
            val appVersion: String
            val analyticsData: AnalyticsPayload

            try {
                deviceId = DeviceIdManager.getDeviceId(context)
                appVersion = getAppVersion(context)
                val platform = "Android"
                val osVersion = Build.VERSION.RELEASE

                analyticsData =
                    AnalyticsPayload(
                        eventName = eventName,
                        deviceId = deviceId,
                        appVersion = appVersion,
                        platform = platform,
                        osVersion = osVersion,
                        properties = properties,
                    )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Frottage disaster! Exception preparing analytics event '$eventName': ${e.message}",
                    e,
                )
                return@launch // Early return if payload creation fails
            }

            try {
                val response = ApiClient.frottageApiService.submitAnalyticsEvent(analyticsData)
                if (!response.isSuccessful) {
                    Log.w(
                        TAG,
                        "Frottage hiccup: Analytics event '$eventName' submission failed (HTTP ${response.code()}). Response: ${response.errorBody()?.string()}",
                    )
                    return@launch // Early return if submission is not successful
                }
                Log.i(
                    TAG,
                    "Analytics event '$eventName' sent successfully (HTTP ${response.code()}). Total frottage achieved!",
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Frottage disaster! Exception sending analytics event '$eventName': ${e.message}",
                    e,
                )
                // No return here, as logging the error is the final action in this catch block for this path.
            }
        }
    }
}
