package com.frottage

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit

@Serializable
data class ImageMetadataValue(
    val image_id: Long,
)

// The JSON is a map where keys are dynamic (targetKey)
// So we parse it as Map<String, ImageMetadataValue>

object ImageMetadataService {
    private const val TAG = "ImageMetadataService"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAndParseImageId(
        context: Context,
        timestampKey: String,
        targetKey: String,
    ): String? {
        val metadataUrlString = "$FROTTAGE_STATIC_BASE_URL/images_$timestampKey.json"
        Log.d(
            TAG,
            "Fetching image metadata from: $metadataUrlString for target: $targetKey. Groovy!",
        )

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(metadataUrlString)
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            try {
                                val fullJson = json.parseToJsonElement(responseBody).jsonObject
                                if (fullJson.containsKey(targetKey)) {
                                    val targetObjectJson = fullJson[targetKey]?.jsonObject
                                    val imageIdLong = targetObjectJson?.get("image_id")?.jsonPrimitive?.longOrNull
                                    if (imageIdLong != null) {
                                        val imageId = imageIdLong.toString()
                                        Log.i(
                                            TAG,
                                            "Successfully fetched and parsed image_id: '$imageId' for target: '$targetKey'. Total frottage!",
                                        )
                                        return@withContext imageId
                                    } else {
                                        Log.w(
                                            TAG,
                                            "Frottage alert: 'image_id' field missing or not a Long in JSON for target '$targetKey' in $metadataUrlString",
                                        )
                                    }
                                } else {
                                    Log.w(
                                        TAG,
                                        "Frottage alert: TargetKey '$targetKey' not found in JSON response from $metadataUrlString",
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Frottage yikes! Failed to parse JSON: ${e.message}", e)
                            }
                        } else {
                            Log.e(TAG, "Frottage fail: Response body was null for $metadataUrlString")
                        }
                    } else {
                        Log.e(
                            TAG,
                            "Frottage fail: HTTP error ${response.code} while fetching $metadataUrlString. Response: ${response.body?.string()}",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Frottage disaster! Exception fetching or parsing image metadata from $metadataUrlString: ${e.message}",
                    e,
                )
            }

            withContext(Dispatchers.Main) {
                Toast
                    .makeText(
                        context,
                        "Frottage hiccup: Could not load image details for rating.",
                        Toast.LENGTH_LONG,
                    ).show()
            }
            return@withContext null
        }
    }
}
