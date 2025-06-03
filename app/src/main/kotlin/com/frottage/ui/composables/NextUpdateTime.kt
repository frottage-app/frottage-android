package com.frottage.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.frottage.SettingsManager
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NextUpdateTime(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    key: Any? = null,
) {
    val now = ZonedDateTime.now(ZoneId.of("UTC"))
    // Assuming SettingsManager.currentWallpaperSource is accessible and correct
    // and that SettingsManager is correctly imported from com.frottage
    val nextUpdateTime = SettingsManager.currentWallpaperSource.schedule.nextUpdateTime(now)
    val localNextUpdateTime = nextUpdateTime.withZoneSameInstant(ZoneId.systemDefault())
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    val formattedNextUpdateTime = localNextUpdateTime.format(timeFormat)

    var tapCount by remember { mutableIntStateOf(0) }

    Text(
        text = "Next image: $formattedNextUpdateTime",
        modifier =
            modifier.clickable {
                tapCount++
                if (tapCount >= 7) {
                    navController.navigate("logscreen")
                    tapCount = 0
                }
            },
    )
}
