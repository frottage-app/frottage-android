package com.frottage

import android.content.Context
import android.util.Log
import androidx.core.content.edit

object RatingPersistence {
    private const val PREFS_NAME = "FrottageImageRatingsPrefs"
    private const val TAG = "RatingPersistence"

    fun saveRating(
        context: Context,
        imageUniqueId: String,
        rating: Int,
    ) {
        if (imageUniqueId.isBlank()) {
            Log.w(TAG, "Attempted to save rating with a blank image ID. That's a frottage no-go!")
            return
        }
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putInt(imageUniqueId, rating)
                Log.i(TAG, "Groovy! Saved rating $rating for image ID: '$imageUniqueId'. This is some top-tier frottage!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frottage fail! Couldn't save rating for image ID '$imageUniqueId'. Bummer: ${e.message}", e)
        }
    }

    fun loadRating(
        context: Context,
        imageUniqueId: String,
    ): Int {
        if (imageUniqueId.isBlank()) {
            Log.w(TAG, "Trying to load a rating with a blank image ID? That's not very frottage of you.")
            return 0
        }
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rating = prefs.getInt(imageUniqueId, 0) // Default to 0 (no rating)
            if (rating != 0) {
                Log.d(TAG, "Loaded rating $rating for image ID: '$imageUniqueId'. Looks like some frottage happened here already!")
            } else {
                Log.d(TAG, "No rating found for image ID: '$imageUniqueId'. Fresh frottage canvas!")
            }
            return rating
        } catch (e: Exception) {
            Log.e(TAG, "Oh dear, a frottage mishap! Couldn't load rating for image ID '$imageUniqueId': ${e.message}", e)
            return 0 // Default to 0 on error
        }
    }
}
