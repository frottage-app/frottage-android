package com.frottage

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ImageMetadataService {
    private const val TAG = "ImageMetadataService"

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
            var connection: HttpURLConnection? = null
            try {
                val url = URL(metadataUrlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000 // 5 seconds
                connection.readTimeout = 5000 // 5 seconds

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonObject = JSONObject(response)
                    if (jsonObject.has(targetKey)) {
                        val targetObject = jsonObject.getJSONObject(targetKey)
                        if (targetObject.has("image_id")) {
                            val imageId = targetObject.getLong("image_id").toString()
                            Log.i(
                                TAG,
                                "Successfully fetched and parsed image_id: '$imageId' for target: '$targetKey'. Total frottage!",
                            )
                            return@withContext imageId
                        } else {
                            Log.w(
                                TAG,
                                "Frottage alert: 'image_id' field missing in JSON for target '$targetKey' in $metadataUrlString",
                            )
                        }
                    } else {
                        Log.w(
                            TAG,
                            "Frottage alert: TargetKey '$targetKey' not found in JSON response from $metadataUrlString",
                        )
                    }
                } else {
                    Log.e(
                        TAG,
                        "Frottage fail: HTTP error ${connection.responseCode} while fetching $metadataUrlString",
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Frottage disaster! Exception fetching or parsing image metadata from $metadataUrlString: ${e.message}",
                    e,
                )
            } finally {
                connection?.disconnect()
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
