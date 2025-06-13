package com.frottage

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RatingService {
    private const val TAG = "StarRatingSvc"

    internal suspend fun submitRating(
        context: Context,
        rating: Int,
        imageIdString: String?,
    ) {
        if (imageIdString == null || imageIdString.isBlank()) {
            Log.e(
                TAG,
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
                TAG,
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
            TAG,
            "Attempting to submit rating: $rating stars for imageId: $imageIdLong ($imageIdString) using Retrofit",
        )

        val deviceId = DeviceIdManager.getDeviceId(context)
        val ratingData =
            RatingPayload(
                imageId = imageIdLong,
                stars = rating,
                deviceId = deviceId,
            )

        try {
            val response = ApiClient.frottageApiService.submitRating(ratingData)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    Log.i(
                        TAG,
                        "Rating submitted successfully to server (HTTP ${response.code()}) for imageId: $imageIdLong, stars: $rating. Awesome frottage!",
                    )
                } else {
                    Log.e(
                        TAG,
                        "Failed to submit rating to server (HTTP ${response.code()}) for imageId: $imageIdLong. Response: ${response.errorBody()?.string()}. This is a frottage bummer.",
                    )
                    Toast
                        .makeText(context, "Failed to submit rating. Please try again.", Toast.LENGTH_LONG)
                        .show()
                }
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Exception submitting rating via Retrofit for imageId $imageIdLong, stars: $rating: ${e.message}",
                e,
            )
            withContext(Dispatchers.Main) {
                Toast
                    .makeText(
                        context,
                        "Frottage network error submitting rating. Please try again.",
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }
}
