package com.frottage

import android.content.Context
import android.content.res.Configuration
import java.time.ZoneId
import java.time.ZonedDateTime


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

const val frottageStaticBaseUrl = "https://frottage.app/static"

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

fun getFrottageUrlForActivePeriod(context: Context, schedule: Schedule): String {
    val targetKey = getFrottageTargetKey(context)
    val now = ZonedDateTime.now(ZoneId.of("UTC"))
    val timestampKey = schedule.getActivePeriodTimestampKey(now)
    return "${frottageStaticBaseUrl}/wallpaper-${targetKey}-${timestampKey}.jpg"
}

val frottageSchedule = UtcHoursSchedule(listOf(1, 7, 13, 19))
val frottageWallpaperSource = WallpaperSource(
    schedule = frottageSchedule,
    lockScreen = ScreenSetting(
        url = { context -> getFrottageUrlForActivePeriod(context, frottageSchedule) },
        blurred = { _ -> false },
    ),
    homeScreen = ScreenSetting(
        url = { context -> getFrottageUrlForActivePeriod(context, frottageSchedule) },
        blurred = { context -> SettingsManager.getHomeScreenBlur(context) },
    ),
)

val unsplashSchedule = EveryXSecondsSchedule(15)
val unsplashWallpaperSource = WallpaperSource(
    schedule = unsplashSchedule,
    lockScreen = ScreenSetting(
        url = { context ->
            val timestampKey =
                unsplashSchedule.getActivePeriodTimestampKey(ZonedDateTime.now(ZoneId.of("UTC")))
            "https://unsplash.it/1080/2400/?random&timestamp=${timestampKey}"
        },
        blurred = { _ -> false },
    ),
    homeScreen = ScreenSetting(
        url = { context ->
            val timestampKey =
                unsplashSchedule.getActivePeriodTimestampKey(ZonedDateTime.now(ZoneId.of("UTC")))
            "https://unsplash.it/1080/2400/?random&timestamp=${timestampKey}"
        },
        blurred = { context -> SettingsManager.getHomeScreenBlur(context) },
    ),
)


