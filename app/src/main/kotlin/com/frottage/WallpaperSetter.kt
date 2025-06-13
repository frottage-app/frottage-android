package com.frottage

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import coil3.Image
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.request.bitmapConfig
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

                val imageUrl = FrottageApiService.getWallpaperUrl(context, activeTimestampKey)
                Log.i(TAG, "Common wallpaper URL: $imageUrl, for activeTimestampKey: $activeTimestampKey")

                val targetKey = FrottageApiService.getFrottageTargetKey(context)

                val baseImageRequest =
                    wallpaperSource.schedule
                        .imageRequest(imageUrl, context, activeTimestampKey, targetKey)

                val imageRequest =
                    baseImageRequest
                        .newBuilder()
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .build()

                val cacheKeyValue = wallpaperSource.schedule.constructCacheKey(targetKey, activeTimestampKey)
                Log.d(TAG, "Downloading original bitmap from $imageUrl with diskCacheKey: $cacheKeyValue")
                logToFile(context, "Downloading original bitmap from $imageUrl with diskCacheKey: $cacheKeyValue")

                val result = imageLoader.execute(imageRequest)

                val coilImage: Image =
                    if (result is SuccessResult) {
                        result.image
                    } else if (result is ErrorResult) {
                        Log.e(TAG, "ImageRequest failed for $imageUrl", result.throwable)
                        logToFile(context, "ImageRequest failed for $imageUrl. Error: ${result.throwable.message}")
                        throw Exception("ImageRequest failed for $imageUrl", result.throwable)
                    } else {
                        Log.e(TAG, "ImageRequest returned an unknown result type for $imageUrl: ${result::class.simpleName}")
                        throw Exception("Failed to load image from $imageUrl. Result was ${result::class.simpleName}")
                    }

                val androidDrawable: android.graphics.drawable.Drawable = coilImage.asDrawable(context.resources)
                val originalBitmap =
                    (androidDrawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        ?: throw Exception("Failed to convert Coil Image to Android Bitmap for $imageUrl")

                if (SettingsManager.getLockScreenEnable(context)) {
                    Log.i(TAG, "Setting Lock Screen wallpaper from $imageUrl")
                    wallpaperManager.setBitmap(originalBitmap, null, true, WallpaperManager.FLAG_LOCK)
                    Log.i(TAG, "Lock Screen wallpaper set.")
                }

                if (SettingsManager.getHomeScreenEnable(context)) {
                    var bitmapForHomeScreen = originalBitmap
                    if (SettingsManager.getHomeScreenBlur(context)) {
                        Log.d(TAG, "Blurring Home Screen image from $imageUrl")
                        bitmapForHomeScreen = blurBitmap(context, originalBitmap, 64.0f)
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
