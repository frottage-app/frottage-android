package com.frottage

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Helper function to fetch promptId for a given targetKey
private suspend fun fetchPromptIdInternal(targetKey: String): Int? {
    try {
        val promptsUrl = URL("https://frottage.app/static/current_prompts.json")
        val promptsJsonString =
                withContext(Dispatchers.IO) {
                    val connection = promptsUrl.openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000 // 5 seconds
                        connection.readTimeout = 5000 // 5 seconds
                        connection.connect()

                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            BufferedReader(InputStreamReader(connection.inputStream)).use { reader
                                ->
                                reader.readText()
                            }
                        } else {
                            Log.e(
                                    "StarRatingSvc",
                                    "Fetch prompts failed with HTTP code: ${connection.responseCode} for target: $targetKey"
                            )
                            null
                        }
                    } finally {
                        connection.disconnect()
                    }
                }

        if (promptsJsonString == null) {
            Log.e(
                    "StarRatingSvc",
                    "Failed to fetch prompts JSON (null response string) for target: $targetKey."
            )
            return null
        }

        val jsonArray = JSONArray(promptsJsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            if (jsonObject.getString("target") == targetKey) {
                return jsonObject.getInt("promptId")
            }
        }
        Log.e("StarRatingSvc", "PromptId not found for target: $targetKey in JSON response.")
        return null
    } catch (e: Exception) {
        Log.e("StarRatingSvc", "Exception fetching promptId for target $targetKey: ${e.message}", e)
        return null
    }
}

// Helper function to post the rating
private suspend fun postRatingInternal(promptId: Int, targetName: String, stars: Int): Boolean {
    try {
        val voteUrl = URL("https://frottage.fly.dev/api/vote")
        val payload =
                JSONObject()
                        .apply {
                            put("promptId", promptId)
                            put("targetName", targetName)
                            put("stars", stars)
                        }
                        .toString()

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
                    "POST request successful (HTTP $responseCode) for promptId: $promptId, target: $targetName"
            )
            true
        } else {
            Log.e(
                    "StarRatingSvc",
                    "POST request failed (HTTP $responseCode) for promptId: $promptId, target: $targetName"
            )
            false
        }
    } catch (e: Exception) {
        Log.e(
                "StarRatingSvc",
                "Exception submitting rating for promptId $promptId: ${e.message}",
                e
        )
        return false
    }
}

// Main function to orchestrate rating submission, callable from other files in the same module
internal suspend fun submitRating(
        context: Context,
        rating: Int,
        targetKey: String,
        imageUrl: String?
) {
    // Guard clause for missing image URL
    if (imageUrl == null) {
        Log.w("StarRatingSvc", "Cannot submit rating, image URL is null.")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error: Image information missing.", Toast.LENGTH_SHORT).show()
        }
        return
    }

    Log.d(
            "StarRatingSvc",
            "Attempting to submit rating: $rating stars for target: '$targetKey', image: $imageUrl"
    )

    // Fetch promptId using the helper function
    val promptId = fetchPromptIdInternal(targetKey)
    if (promptId == null) { // Guard clause if promptId could not be fetched
        Log.e(
                "StarRatingSvc",
                "Failed to obtain promptId for target: $targetKey. Aborting rating submission."
        )
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error: Could not retrieve rating details.", Toast.LENGTH_LONG)
                    .show()
        }
        return
    }

    // Post the rating using the helper function
    val success = postRatingInternal(promptId, targetKey, rating)

    // Show feedback to the user on the main thread
    withContext(Dispatchers.Main) {
        if (success) {
            Log.i(
                    "StarRatingSvc",
                    "Rating submitted successfully to server for promptId: $promptId, target: $targetKey, stars: $rating."
            )
            Toast.makeText(context, "Rated $rating stars for $targetKey!", Toast.LENGTH_SHORT)
                    .show()
        } else {
            Log.e(
                    "StarRatingSvc",
                    "Failed to submit rating to server for promptId: $promptId, target: $targetKey."
            )
            Toast.makeText(context, "Failed to submit rating. Please try again.", Toast.LENGTH_LONG)
                    .show()
        }
    }
}
