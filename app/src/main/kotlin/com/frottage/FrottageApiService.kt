package com.frottage

import android.content.Context
import android.content.res.Configuration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface FrottageApiService {
    @POST("/api/lytics/event")
    suspend fun submitAnalyticsEvent(
            @Body payload: AnalyticsPayload,
    ): Response<Unit>

    // Using @Url for dynamic URLs. The base URL of the Retrofit instance won't be used for this
    // call.
    @GET
    suspend fun getImageMetadata(
            @Url metadataUrl: String,
    ): Map<String, ImageMetadataValue>

    @POST("/api/vote")
    suspend fun submitRating(
            @Body payload: RatingPayload,
    ): Response<Unit>

    companion object {
        const val FROTTAGE_STATIC_BASE_URL = "https://frottage.app/static"

        fun isTablet(context: Context): Boolean {
            val configuration: Configuration = context.resources.configuration
            val smallestScreenWidthDp = configuration.smallestScreenWidthDp
            return smallestScreenWidthDp >= 600
        }

        fun isDarkTheme(context: Context): Boolean =
                when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> true // Dark Theme
                    Configuration.UI_MODE_NIGHT_NO -> false // Light Theme
                    else -> true // Default to Dark Theme
                }

        fun getFrottageTargetKey(context: Context): String =
                if (isTablet(context)) {
                    if (isDarkTheme(context)) {
                        "desktop"
                    } else {
                        "desktop-light"
                    }
                } else {
                    "mobile"
                }

        fun getImagesMetadataUrl(timestampKey: String): String =
                "$FROTTAGE_STATIC_BASE_URL/images_$timestampKey.json"

        fun getWallpaperUrl(
                context: Context,
                timestampKey: String,
        ): String {
            val targetKey = getFrottageTargetKey(context)
            return "$FROTTAGE_STATIC_BASE_URL/wallpaper-$targetKey-$timestampKey.jpg"
        }
    }
}

@Serializable
data class AnalyticsPayload(
        val eventName: String,
        val deviceId: String,
        val appVersion: String,
        val platform: String,
        val osVersion: String,
        val properties: JsonObject? = null,
)

@Serializable
data class ImageMetadataValue(
        val image_id: Long,
        val pure_prompt: String,
)
// For getImageMetadata, the response is Map<String, ImageMetadataValue>

@Serializable
data class RatingPayload(
        val imageId: Long,
        val stars: Int,
        val deviceId: String,
)
