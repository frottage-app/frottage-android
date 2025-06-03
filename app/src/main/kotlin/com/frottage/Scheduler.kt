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
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

fun scheduleNextUpdate(context: Context) {
    val now = ZonedDateTime.now(ZoneId.of("UTC"))
    val nextUpdateTime = SettingsManager.currentWallpaperSource.schedule.nextUpdateTime(now)

    val delay = Duration.between(now, nextUpdateTime).toMillis()

    val wallpaperWorkRequest =
        OneTimeWorkRequestBuilder<WallpaperWorker>()
            .addTag("wallpaper_update")
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
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
                WallpaperSetter.setWallpaper(applicationContext)
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
