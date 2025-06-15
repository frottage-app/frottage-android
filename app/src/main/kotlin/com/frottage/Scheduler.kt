package com.frottage

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

fun scheduleNextUpdate(context: Context) {
    val now = ZonedDateTime.now(ZoneId.of("UTC"))
    val nextUpdateTime = SettingsManager.currentWallpaperSource.schedule.nextUpdateTime(now)

    val notBeforeDelayMillis = Duration.between(now, nextUpdateTime).toMillis()

    val wallpaperWorkRequest =
        OneTimeWorkRequestBuilder<WallpaperWorker>()
            .addTag("wallpaper_update")
            .setInitialDelay(notBeforeDelayMillis, TimeUnit.MILLISECONDS)
            // when constraint is enabled, it triggers only when connected to wifi...
            .setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS,
            ).build()

    WorkManager
        .getInstance(context)
        .enqueueUniqueWork(
            "wallpaper_update",
            ExistingWorkPolicy.REPLACE,
            wallpaperWorkRequest,
        )

    Log.i("scheduleNextUpdate", "Next Update scheduled at: $nextUpdateTime")
    logToFile(context, "Next Update scheduled at: $nextUpdateTime")
}

fun cancelUpdateSchedule(context: Context) {
    WorkManager.getInstance(context).cancelAllWorkByTag("wallpaper_update")
    logToFile(context, "Schedule cancelled")
}

class WallpaperWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                // Calculate activeTimestampKey for the worker
                val now = ZonedDateTime.now(ZoneId.of("UTC"))
                logToFile(applicationContext, "[WorkerDebug] Worker started. Current UTC time (now): $now")

                val wallpaperSource = SettingsManager.currentWallpaperSource
                val schedule = wallpaperSource.schedule
                logToFile(applicationContext, "[WorkerDebug] Using schedule type: ${schedule::class.simpleName}")

                val previousUpdateTime = schedule.prevUpdateTime(now)
                logToFile(applicationContext, "[WorkerDebug] Calculated prevUpdateTime(now): $previousUpdateTime")

                val activeTimestampKey = schedule.getActivePeriodTimestampKey(now) // This internally calls prevUpdateTime(now) again
                logToFile(
                    applicationContext,
                    "[WorkerDebug] Final activeTimestampKey from getActivePeriodTimestampKey(now): $activeTimestampKey",
                )

                WallpaperSetter.setWallpaper(applicationContext, activeTimestampKey)

                // Analytics: Track successful scheduled wallpaper set
                try {
                    val targetKey = FrottageApiService.getFrottageTargetKey(applicationContext)
                    val imageId =
                        if (SettingsManager.currentWallpaperSource.supportsFrottageRatingSystem) {
                            ImageMetadataService.fetchAndParseImageId(
                                applicationContext,
                                activeTimestampKey,
                                targetKey,
                            )
                        } else {
                            null
                        }

                    val jsonProperties =
                        buildJsonObject {
                            put("target_name", JsonPrimitive(targetKey))
                            put("theme", JsonPrimitive(if (FrottageApiService.isDarkTheme(applicationContext)) "dark" else "light"))
                            put("source", JsonPrimitive("schedule"))
                            imageId?.let { put("image_id", JsonPrimitive(it)) }
                            // Add battery optimization status
                            val batteryStatusNullable = BatteryUtils.getBatteryOptimizationExemptionStatusForAnalytics(applicationContext)
                            if (batteryStatusNullable != null) {
                                put("battery_optimization_exempt", JsonPrimitive(batteryStatusNullable))
                            } else {
                                put("battery_optimization_exempt", JsonPrimitive("not_applicable_below_M"))
                            }
                        }

                    AnalyticsService.trackEvent(
                        context = applicationContext,
                        eventName = "set_wallpaper",
                        properties = jsonProperties,
                    )
                } catch (analyticsException: Exception) {
                    Log.e(
                        "WallpaperWorker",
                        "Frottage hiccup: Failed to send 'set_wallpaper' analytics event: ${analyticsException.message}",
                        analyticsException,
                    )
                    logToFile(
                        applicationContext,
                        "Worker: Failed to send analytics event 'set_wallpaper': ${analyticsException.message}",
                    )
                }

                scheduleNextUpdate(applicationContext)
                Result.success()
            } catch (e: Exception) {
                Log.e("WallpaperWorker", "Failed to set wallpaper: ${e.message}", e)
                logToFile(
                    applicationContext,
                    "Worker failed, will retry: ${e.message}\n${e.stackTraceToString()}",
                )
                Result.retry()
            }
        }
}
