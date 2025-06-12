package com.frottage

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val API_HOST_ANALYTICS = "https://frottage.fly.dev"
private const val API_PATH_ANALYTICS = "/api/analytics/event"

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
        eventProperties: Map<String, Any?> = emptyMap(),
    ) {
        // Launch in a coroutine so we don't block the calling thread (typically UI)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = DeviceIdManager.getDeviceId(context)
                val appVersion = getAppVersion(context)
                val platform = "Android"
                val osVersion = Build.VERSION.RELEASE

                val propertiesJson = JSONObject()
                for ((key, value) in eventProperties) {
                    propertiesJson.put(key, value)
                }

                val payload =
                    JSONObject()
                        .apply {
                            put("eventName", eventName)
                            put("deviceId", deviceId)
                            put("appVersion", appVersion)
                            put("platform", platform)
                            put("osVersion", osVersion)
                            put("properties", propertiesJson)
                        }.toString()

                val eventUrl = URL(API_HOST_ANALYTICS + API_PATH_ANALYTICS)
                Log.d(
                    TAG,
                    "Tracking analytics event: '$eventName'. Payload: $payload. Endpoint: $eventUrl. This is some groovy tracking!",
                )

                val connection = eventUrl.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.connectTimeout = 10000 // 10 seconds
                    connection.readTimeout = 10000 // 10 seconds
                    connection.doOutput = true

                    OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                        writer.write(payload)
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        Log.i(
                            TAG,
                            "Analytics event '$eventName' sent successfully (HTTP $responseCode). Total frottage achieved!",
                        )
                    } else {
                        Log.w(
                            TAG,
                            "Frottage hiccup: Analytics event '$eventName' submission failed (HTTP $responseCode). Response: ${connection.errorStream?.bufferedReader()?.readText()}",
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Frottage disaster! Exception sending analytics event '$eventName': ${e.message}",
                    e,
                )
                // As per decision, log and continue
            }
        }
    }
}
