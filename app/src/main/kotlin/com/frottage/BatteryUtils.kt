package com.frottage

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

object BatteryUtils {
    private const val TAG = "BatteryUtils"

    /**
     * Checks if the app is ignoring battery optimizations.
     *
     * @param context The application context.
     * @return `true` if ignoring optimizations,
     *         `false` if not ignoring optimizations (API >= M),
     *         or `null` if API < M (not applicable in the same direct way for this check).
     */
    fun getBatteryOptimizationExemptionStatusForAnalytics(context: Context): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            Log.d(TAG, "Groovy! Battery optimization status: isIgnoring = $isIgnoring for package ${context.packageName}")
            isIgnoring
        } else {
            Log.d(TAG, "Groovy! Battery optimization check not directly applicable below Android M, returning null for analytics.")
            null
        }
}
