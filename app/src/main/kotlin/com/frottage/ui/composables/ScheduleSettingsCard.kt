package com.frottage.ui.composables

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.frottage.AnalyticsService
import com.frottage.BatteryUtils
import com.frottage.SettingsManager
import com.frottage.cancelUpdateSchedule
import com.frottage.logToFile
import com.frottage.scheduleNextUpdate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Composable
fun ScheduleSettingsCard(
    navController: NavHostController,
    currentTimestampKey: String?, // Used as a key for NextUpdateTime
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.width(300.dp),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    start = 10.dp,
                    top = 18.dp,
                    end = 20.dp,
                    bottom = 8.dp,
                ),
        ) {
            Text(
                text = "Schedule",
                style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                modifier =
                    Modifier.padding(
                        start = 12.dp,
                        bottom = 10.dp,
                    ),
            )
            NextUpdateTime( // This is the existing Composable from com.frottage.ui.composables
                navController = navController,
                key = currentTimestampKey, // Pass the key to allow recomposition if it changes
                modifier = Modifier.padding(start = 12.dp),
            )
            ScheduleSwitch(
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun ScheduleSwitch(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isScheduleEnabled by remember {
        mutableStateOf(
            SettingsManager.getScheduleIsEnabled(context),
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("Enable schedule")
        Switch(
            checked = isScheduleEnabled,
            onCheckedChange = { enabled ->
                isScheduleEnabled = enabled
                SettingsManager.setScheduleIsEnabled(
                    context,
                    isScheduleEnabled,
                )
                if (enabled) {
                    logToFile(context, "Schedule enabled. Let the frottage flow!")
                    requestBatteryOptimizationExemption(context)

                    val properties =
                        buildJsonObject {
                            val batteryStatusNullable =
                                BatteryUtils
                                    .getBatteryOptimizationExemptionStatusForAnalytics(
                                        context,
                                    )
                            if (batteryStatusNullable != null) {
                                put(
                                    "battery_optimization_exempt",
                                    JsonPrimitive(batteryStatusNullable),
                                )
                            } else {
                                put(
                                    "battery_optimization_exempt",
                                    JsonPrimitive("not_applicable_below_M"),
                                )
                            }
                        }

                    scheduleNextUpdate(context)
                    AnalyticsService.trackEvent(context, "enable_schedule", properties)
                } else {
                    logToFile(context, "Schedule disabled. Frottage paused for now.")
                    cancelUpdateSchedule(context)
                    AnalyticsService.trackEvent(context, "disable_schedule")
                }
            },
        )
    }
}

private fun requestBatteryOptimizationExemption(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent =
                Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:${context.packageName}")
                    // It's generally safer to add this flag if starting from a non-Activity context,
                    // though for this specific system action, it might not always be strictly necessary.
                    // However, if called from a BroadcastReceiver or Service context indirectly, it would be.
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            try {
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                Log.e(
                    "ScheduleSettingsCard",
                    "Frottage fail! No activity found to handle ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. This is not groovy!",
                    e,
                )
            } catch (e: Exception) {
                // Catch any other potential exceptions, e.g., SecurityException if permissions are somehow messed up
                // (though unlikely for this particular intent).
                Log.e(
                    "ScheduleSettingsCard",
                    "Frottage hiccup! Could not start activity for battery optimization exemption: ${e.message}",
                    e,
                )
            }
        } else {
            Log.d("ScheduleSettingsCard", "Groovy! App is already ignoring battery optimizations.")
        }
    } else {
        Log.d("ScheduleSettingsCard", "Battery optimization exemption request not applicable below Android M. Keep on frottaging!")
    }
}
