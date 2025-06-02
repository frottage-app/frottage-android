package com.frottage

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

object WallpaperSetter {
    private const val TAG = "WallpaperSetter"

    suspend fun setWallpaper(context: Context) {
        Log.d(TAG, "Starting wallpaper update process")
        logToFile(context, "Starting wallpaper update process")
        try {
            withContext(Dispatchers.IO) {
                val now = ZonedDateTime.now(ZoneId.of("UTC"))
                val wallpaperSource = SettingsManager.currentWallpaperSource

                if (SettingsManager.getLockScreenEnable(context)) {
                    wallpaperSource.lockScreen?.let {
                        val lockScreenUrl = it.url(context)
                        setWallpaperForScreen(
                            context,
                            lockScreenUrl,
                            it.blurred,
                            WallpaperManager.FLAG_LOCK, // Lock Screen
                            wallpaperSource.schedule.imageRequest(lockScreenUrl, now, context),
                        )
                    }
                }

                if (SettingsManager.getHomeScreenEnable(context)) {
                    wallpaperSource.homeScreen?.let {
                        val homeScreenUrl = it.url(context)
                        setWallpaperForScreen(
                            context,
                            homeScreenUrl,
                            it.blurred,
                            WallpaperManager.FLAG_SYSTEM, // Home screen
                            wallpaperSource.schedule.imageRequest(homeScreenUrl, now, context),
                        )
                    }
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

    private suspend fun setWallpaperForScreen(
        context: Context,
        url: String,
        blurred: (Context) -> Boolean,
        flag: Int,
        imageRequest: ImageRequest,
    ) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val imageLoader = ImageLoader(context)
        val imageRequest = imageRequest.newBuilder().allowHardware(false).build()

        Log.i(TAG, "Downloading wallpaper from $url")
        logToFile(context, "Downloading wallpaper from $url")

        val image =
            (imageLoader.execute(imageRequest) as? SuccessResult)?.drawable
                ?: throw Exception("Failed to load image from $url")

        var bitmap = (image as android.graphics.drawable.BitmapDrawable).bitmap
        if (blurred(context)) {
            bitmap = blurBitmap(context, bitmap, 64.0f)
        }
        wallpaperManager.setBitmap(bitmap, null, true, flag)
    }
}
