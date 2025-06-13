package com.frottage

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

// private const val API_HOST = "http://10.0.2.2:3000" // for local testing
private const val API_HOST = "https://frottage.fly.dev"

private val client =
    OkHttpClient
        .Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

private suspend fun postRatingInternal(
    imageId: Long,
    stars: Int,
    deviceId: String,
): Boolean =
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

        val requestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request =
            Request
                .Builder()
                .url(voteUrl)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .build()

        withContext(Dispatchers.IO) {
            // Ensure network call is off the main thread
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(
                        "StarRatingSvc",
                        "POST request successful (HTTP ${response.code}) for imageId: $imageId, stars: $stars. How groovy is that?!",
                    )
                    true // return true from withContext block
                } else {
                    Log.e(
                        "StarRatingSvc",
                        "POST request failed (HTTP ${response.code}) for imageId: $imageId, stars: $stars. Response: ${response.body?.string()}. What a frottage shame.",
                    )
                    false // return false from withContext block
                }
            }
        }
    } catch (e: Exception) {
        Log.e(
            "StarRatingSvc",
            "Exception submitting rating for imageId $imageId, stars: $stars: ${e.message}",
            e,
        )
        false // return false in case of exception
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
