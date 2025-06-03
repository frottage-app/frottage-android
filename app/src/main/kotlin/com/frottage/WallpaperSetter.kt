package com.frottage

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperSetter {
    private const val TAG = "WallpaperSetter"

    suspend fun setWallpaper(
        context: Context,
        activeTimestampKey: String,
    ) {
        Log.d(TAG, "Starting wallpaper update process for timestampKey: $activeTimestampKey")
        logToFile(context, "Starting wallpaper update process for timestampKey: $activeTimestampKey")
        try {
            withContext(Dispatchers.IO) {
                val wallpaperSource = SettingsManager.currentWallpaperSource
                val wallpaperManager = WallpaperManager.getInstance(context)
                val imageLoader = ImageLoader(context)

                // Use the passed activeTimestampKey for the URL
                val imageUrl = wallpaperSource.imageSetting.url(context, activeTimestampKey)
                Log.i(TAG, "Common wallpaper URL: $imageUrl, for activeTimestampKey: $activeTimestampKey")

                // Fetch the original bitmap once, using activeTimestampKey for disk cache
                val imageRequest =
                    wallpaperSource.schedule
                        .imageRequest(imageUrl, context, activeTimestampKey)
                        .newBuilder()
                        .allowHardware(false)
                        .build()

                Log.d(TAG, "Downloading original bitmap from $imageUrl with diskCacheKey: $activeTimestampKey")
                logToFile(context, "Downloading original bitmap from $imageUrl with diskCacheKey: $activeTimestampKey")
                val result = (imageLoader.execute(imageRequest) as? SuccessResult)?.drawable
                val originalBitmap =
                    (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        ?: throw Exception("Failed to load original image from $imageUrl")

                // Set for Lock Screen if enabled (typically unblurred)
                if (SettingsManager.getLockScreenEnable(context)) {
                    Log.i(TAG, "Setting Lock Screen wallpaper from $imageUrl")
                    // For now, assume lock screen is not blurred by default by source setting.
                    // If lock screen blur becomes configurable, add logic here.
                    wallpaperManager.setBitmap(originalBitmap, null, true, WallpaperManager.FLAG_LOCK)
                    Log.i(TAG, "Lock Screen wallpaper set.")
                }

                // Set for Home Screen if enabled
                if (SettingsManager.getHomeScreenEnable(context)) {
                    var bitmapForHomeScreen = originalBitmap // Start with the original
                    if (SettingsManager.getHomeScreenBlur(context)) {
                        Log.d(TAG, "Blurring Home Screen image from $imageUrl")
                        bitmapForHomeScreen = blurBitmap(context, originalBitmap, 64.0f) // Blur the original
                    }
                    Log.i(TAG, "Setting Home Screen wallpaper from $imageUrl")
                    wallpaperManager.setBitmap(bitmapForHomeScreen, null, true, WallpaperManager.FLAG_SYSTEM)
                    Log.i(TAG, "Home Screen wallpaper set.")
                }
            }
            Log.i(TAG, "Wallpapers set successfully")
            logToFile(context, "Wallpapers set successfully")
        } catch (e: Exception) {
            val errorMessage = "Failed to set wallpaper: ${e.message}"
            Log.e(TAG, errorMessage, e)
            logToFile(context, "$errorMessage\n${e.stackTraceToString()}")
            throw e
        }
    }
}
