package com.frottage

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.concurrent.TimeUnit

private const val API_HOST_ANALYTICS = "https://frottage.fly.dev"
private const val API_PATH_ANALYTICS = "/api/analytics/event"

@Serializable
private data class AnalyticsPayload(
    val eventName: String,
    val deviceId: String,
    val appVersion: String,
    val platform: String,
    val osVersion: String,
    val properties: Map<String, String?>, // Assuming properties are mostly strings or null
)

object AnalyticsService {
    private const val TAG = "AnalyticsService"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    // Configure a Json instance
    private val json = Json { ignoreUnknownKeys = true }

    private fun getAppVersion(context: Context): String =
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown_version"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Frottage Alert! Could not get package info to determine app version.", e)
            "unknown_version"
        }

    @Suppress("UNCHECKED_CAST")
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

                // Convert eventProperties to Map<String, String?> for serialization
                // This is a simplification; if properties have non-string values,
                // this needs more robust handling (e.g., Map<String, JsonElement>
                // or custom serializers if structure is complex but known).
                val serializableProperties =
                    eventProperties.mapValues { entry ->
                        entry.value?.toString()
                    }

                val analyticsData =
                    AnalyticsPayload(
                        eventName = eventName,
                        deviceId = deviceId,
                        appVersion = appVersion,
                        platform = platform,
                        osVersion = osVersion,
                        properties = serializableProperties,
                    )

                val payload = json.encodeToString(analyticsData)

                val eventUrl = URL(API_HOST_ANALYTICS + API_PATH_ANALYTICS)
                Log.d(
                    TAG,
                    "Tracking analytics event: '$eventName'. Payload: $payload. Endpoint: $eventUrl. This is some groovy tracking!",
                )

                val requestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request =
                    Request
                        .Builder()
                        .url(eventUrl)
                        .post(requestBody)
                        .addHeader("Accept", "application/json")
                        .build()

                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.i(
                                TAG,
                                "Analytics event '$eventName' sent successfully (HTTP ${response.code}). Total frottage achieved!",
                            )
                        } else {
                            Log.w(
                                TAG,
                                "Frottage hiccup: Analytics event '$eventName' submission failed (HTTP ${response.code}). Response: ${response.body?.string()}",
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Frottage disaster! Exception sending analytics event '$eventName': ${e.message}",
                        e,
                    )
                    // As per decision, log and continue
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
