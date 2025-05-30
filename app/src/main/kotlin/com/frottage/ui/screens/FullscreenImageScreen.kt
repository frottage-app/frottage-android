package com.frottage.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.frottage.SettingsManager
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun FullscreenImageScreen(onClick: () -> Unit) {
    val context = LocalContext.current
    var alreadyClicked by remember { mutableStateOf(false) }
    val wallpaperSource =
        SettingsManager.currentWallpaperSource
    wallpaperSource.lockScreen?.let {
        val lockScreenUrl = it.url(context)
        val now = ZonedDateTime.now(ZoneId.of("UTC"))
        val imageRequest =
            wallpaperSource.schedule.imageRequest(
                lockScreenUrl,
                now,
                context,
            )
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .clickable {
                    if (!alreadyClicked) {
                        alreadyClicked = true
                        onClick()
                    }
                },
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Current Lock Screen Wallpaper",
                modifier =
                Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}