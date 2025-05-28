package com.frottage

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val PREFS_NAME = "FrottagePrefs"
private const val DEVICE_ID_KEY = "myFrottageDeviceId"
// private const val API_HOST = "http://10.0.2.2:3000" // for local testing
private const val API_HOST = "https://frottage.fly.dev"

private fun getMyDeviceId(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var deviceId = prefs.getString(DEVICE_ID_KEY, null)
    if (deviceId == null) {
        deviceId = UUID.randomUUID().toString()
        prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply()
        Log.i("StarRatingSvc", "Groovy! Generated new deviceId: $deviceId")
    } else {
        Log.d("StarRatingSvc", "Fetched existing deviceId: $deviceId")
    }
    return deviceId
}

private suspend fun postRatingInternal(targetName: String, stars: Int, deviceId: String): Boolean {
    try {
        val voteUrl = URL("$API_HOST/api/vote_latest_by_target") // Using the constant
        val payload =
            JSONObject()
                .apply {
                    put("targetName", targetName)
                    put("stars", stars)
                    put("deviceId", deviceId)
                }
                .toString()

        Log.d("StarRatingSvc", "Posting rating with payload: $payload to URL: $voteUrl")

        val responseCode =
            withContext(Dispatchers.IO) {
                val connection = voteUrl.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                    )
                    connection.setRequestProperty("Accept", "application/json")
                    connection.connectTimeout = 5000 // 5 seconds
                    connection.readTimeout = 5000 // 5 seconds
                    connection.doOutput = true

                    OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                        writer.write(payload)
                        writer.flush()
                    }
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
            }
        return if (responseCode in 200..299) {
            Log.i(
                "StarRatingSvc",
                "POST request successful (HTTP $responseCode) for target: $targetName, stars: $stars, deviceId: $deviceId. How groovy is that?!"
            )
            true
        } else {
            Log.e(
                "StarRatingSvc",
                "POST request failed (HTTP $responseCode) for target: $targetName, stars: $stars, deviceId: $deviceId. What a frottage shame."
            )
            false
        }
    } catch (e: Exception) {
        Log.e(
            "StarRatingSvc",
            "Exception submitting rating for target $targetName, stars: $stars, deviceId: $deviceId: ${e.message}",
            e
        )
        return false
    }
}

internal suspend fun submitRating(
    context: Context,
    rating: Int,
    targetKey: String
) {
    Log.d(
        "StarRatingSvc",
        "Attempting to submit rating: $rating stars for target: '$targetKey'"
    )

    // Get device ID
    val deviceId = getMyDeviceId(context)

    // Post the rating using the helper function
    val success = postRatingInternal(targetKey, rating, deviceId)

    // Show feedback to the user on the main thread
    withContext(Dispatchers.Main) {
        if (success) {
            Log.i(
                "StarRatingSvc",
                "Rating submitted successfully to server for target: $targetKey, stars: $rating. Awesome frottage!"
            )
            Toast.makeText(context, "Rated $rating stars for $targetKey!", Toast.LENGTH_SHORT)
                .show()
        } else {
            Log.e(
                "StarRatingSvc",
                "Failed to submit rating to server for target: $targetKey. This is a frottage bummer."
            )
            Toast.makeText(context, "Failed to submit rating. Please try again.", Toast.LENGTH_LONG)
                .show()
        }
    }
}
