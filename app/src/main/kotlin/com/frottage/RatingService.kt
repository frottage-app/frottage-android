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

// private const val API_HOST = "http://10.0.2.2:3000" // for local testing
private const val API_HOST = "https://frottage.fly.dev"

private suspend fun postRatingInternal(
    imageId: Long,
    stars: Int,
    deviceId: String,
): Boolean {
    try {
        val voteUrl = URL("$API_HOST/api/vote")
        val payload =
            JSONObject()
                .apply {
                    put("imageId", imageId)
                    put("stars", stars)
                    put("deviceId", deviceId)
                }.toString()

        Log.d(
            "StarRatingSvc",
            "Posting rating with payload: $payload to URL: $voteUrl. This is so frottage!",
        )

        val responseCode =
            withContext(Dispatchers.IO) {
                val connection = voteUrl.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8",
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
                "POST request successful (HTTP $responseCode) for imageId: $imageId, stars: $stars. How groovy is that?!",
            )
            true
        } else {
            Log.e(
                "StarRatingSvc",
                "POST request failed (HTTP $responseCode) for imageId: $imageId, stars: $stars. What a frottage shame.",
            )
            false
        }
    } catch (e: Exception) {
        Log.e(
            "StarRatingSvc",
            "Exception submitting rating for imageId $imageId, stars: $stars: ${e.message}",
            e,
        )
        return false
    }
}

internal suspend fun submitRating(
    context: Context,
    rating: Int,
    imageIdString: String?,
) {
    if (imageIdString == null || imageIdString.isBlank()) {
        Log.e(
            "StarRatingSvc",
            "Frottage Alert! Attempting to submit rating with null or blank imageIdString. Cannot proceed.",
        )
        withContext(Dispatchers.Main) {
            Toast
                .makeText(context, "Cannot submit rating: Image ID missing.", Toast.LENGTH_LONG)
                .show()
        }
        return
    }

    val imageIdLong: Long
    try {
        imageIdLong = imageIdString.toLong()
    } catch (e: NumberFormatException) {
        Log.e(
            "StarRatingSvc",
            "Frottage critical error! imageIdString '$imageIdString' is not a valid Long. Cannot submit rating.",
            e,
        )
        withContext(Dispatchers.Main) {
            Toast
                .makeText(
                    context,
                    "Cannot submit rating: Invalid Image ID format.",
                    Toast.LENGTH_LONG,
                ).show()
        }
        return
    }

    Log.d(
        "StarRatingSvc",
        "Attempting to submit rating: $rating stars for imageId: $imageIdLong ($imageIdString)",
    )

    val deviceId = DeviceIdManager.getDeviceId(context)
    val success = postRatingInternal(imageIdLong, rating, deviceId)

    withContext(Dispatchers.Main) {
        if (success) {
            Log.i(
                "StarRatingSvc",
                "Rating submitted successfully to server for imageId: $imageIdLong, stars: $rating. Awesome frottage!",
            )
        } else {
            Log.e(
                "StarRatingSvc",
                "Failed to submit rating to server for imageId: $imageIdLong. This is a frottage bummer.",
            )
            // Toast is already handled in fetchAndParseImageId for metadata fetch issues,
            // but we might want a specific one for rating submission failure itself.
            Toast
                .makeText(context, "Failed to submit rating. Please try again.", Toast.LENGTH_LONG)
                .show()
        }
    }
}
