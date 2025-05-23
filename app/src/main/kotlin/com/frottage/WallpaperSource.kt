package com.frottage

import android.content.Context
import android.content.res.Configuration


data class WallpaperSource(
    val schedule: Schedule,
    val lockScreen: ScreenSetting?,
    val homeScreen: ScreenSetting?,
)

data class ScreenSetting(
    val url: (Context) -> String,
    val blurred: (Context) -> Boolean,
)


fun isTablet(context: Context): Boolean {
    val configuration: Configuration = context.resources.configuration
    val smallestScreenWidthDp = configuration.smallestScreenWidthDp
    return smallestScreenWidthDp >= 600
}

fun isDarkTheme(context: Context): Boolean {
    return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true  // Dark Theme
        Configuration.UI_MODE_NIGHT_NO -> false  // Light Theme
        else -> true  // Default to Dark Theme
    }
}

const val baseUrl = "https://frottage.app/static"

fun frottageUrl(context: Context): String {
    val targetKey = getFrottageTargetKey(context) // e.g. "desktop", "desktop-light", "mobile"
    val filename = "wallpaper-${targetKey}-latest.jpg"
    return "${baseUrl}/${filename}"
}

fun getFrottageTargetKey(context: Context): String {
    return if (isTablet(context)) {
        if (isDarkTheme(context)) {
            "desktop"
        } else {
            "desktop-light"
        }
    } else {
        "mobile"
    }
}


val frottageWallpaperSource = WallpaperSource(
    schedule = UtcHoursSchedule(listOf(1, 7, 13, 19)),
    lockScreen = ScreenSetting(
        url = { context -> frottageUrl(context) },
        blurred = { _ -> false },
    ),
    homeScreen = ScreenSetting(
        url = { context -> frottageUrl(context) },
        blurred = { context -> SettingsManager.getHomeScreenBlur(context) },
    ),
)

val unsplashWallpaperSource = WallpaperSource(
    schedule = EveryXSecondsSchedule(15),
    lockScreen = ScreenSetting(
        url = { context -> "https://unsplash.it/1080/2400/?random" },
        blurred = { _ -> false },
    ),
    homeScreen = ScreenSetting(
        url = { context -> "https://unsplash.it/1080/2400/?random" },
        blurred = { context -> SettingsManager.getHomeScreenBlur(context) },
    ),
)


