package com.frottage

import android.content.Context
import androidx.core.content.edit

object SettingsManager {
    val currentWallpaperSource = frottageWallpaperSource

    private const val PREFS_NAME = "FrottageSettings"
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_HOME_SCREEN_BLUR = "home_screen_blur"
    private const val KEY_HOME_SCREEN_ENABLE = "home_screen_enable"
    private const val KEY_LOCK_SCREEN_ENABLE = "lock_screen_enable"
    private const val KEY_FIRST_LAUNCH_EVENT_SENT = "first_launch_event_sent"
    private const val KEY_IN_APP_REVIEW_REQUESTED = "in_app_review_requested"

    fun setScheduleIsEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SCHEDULE_ENABLED, enabled)
        }
    }

    fun getScheduleIsEnabled(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCHEDULE_ENABLED, false)

    fun setHomeScreenBlur(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_HOME_SCREEN_BLUR, enabled)
        }
    }

    fun setHomeScreenEnable(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_HOME_SCREEN_ENABLE, enabled)
        }
    }

    fun setLockScreenEnable(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_LOCK_SCREEN_ENABLE, enabled)
        }
    }

    fun getHomeScreenBlur(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HOME_SCREEN_BLUR, true)

    fun getHomeScreenEnable(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HOME_SCREEN_ENABLE, true)

    fun getLockScreenEnable(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCK_SCREEN_ENABLE, true)

    fun setFirstLaunchEventSent(
        context: Context,
        sent: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_FIRST_LAUNCH_EVENT_SENT, sent)
        }
    }

    fun getFirstLaunchEventSent(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_LAUNCH_EVENT_SENT, false)

    fun setInAppReviewRequested(
        context: Context,
        requested: Boolean,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_IN_APP_REVIEW_REQUESTED, requested)
        }
    }

    fun getInAppReviewRequested(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IN_APP_REVIEW_REQUESTED, false)
}
