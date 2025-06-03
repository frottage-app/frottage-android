package com.frottage

import android.content.Context
import android.content.res.Configuration

data class WallpaperSource(
    val schedule: Schedule,
    val imageSetting: ScreenSetting,
    val supportsFrottageRatingSystem: Boolean,
)

data class ScreenSetting(
    val url: (context: Context, timestampKey: String) -> String,
)

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

const val FROTTAGE_STATIC_BASE_URL = "https://frottage.app/static"

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

val frottageWallpaperSource =
    WallpaperSource(
        schedule = UtcHoursSchedule(listOf(1, 7, 13, 19)),
        imageSetting =
            ScreenSetting(
                url = { context, timestampKey ->
                    val targetKey = getFrottageTargetKey(context)
                    "$FROTTAGE_STATIC_BASE_URL/wallpaper-$targetKey-$timestampKey.jpg"
                },
            ),
        supportsFrottageRatingSystem = true,
    )

val unsplashWallpaperSource =
    WallpaperSource(
        schedule = EveryXSecondsSchedule(15),
        imageSetting =
            ScreenSetting(
                url = { _, _ ->
                    "https://unsplash.it/1080/2400/?random"
                },
            ),
        supportsFrottageRatingSystem = false,
    )
