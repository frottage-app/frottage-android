package com.frottage

import android.content.Context

data class WallpaperSource(
    val schedule: Schedule,
    val imageSetting: ScreenSetting,
    val supportsFrottageRatingSystem: Boolean,
)

data class ScreenSetting(
    val url: (context: Context, timestampKey: String) -> String,
)

val frottageWallpaperSource =
    WallpaperSource(
        schedule = UtcHoursSchedule(listOf(1, 7, 13, 19)),
        imageSetting =
            ScreenSetting(
                url = { context, timestampKey ->
                    FrottageApiService.getWallpaperUrl(context, timestampKey)
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
