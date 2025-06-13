package com.frottage.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.frottage.FrottageApiService
import com.frottage.SettingsManager

@Composable
fun FullscreenImageScreen(
    onClick: () -> Unit,
    timestampKey: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var alreadyClicked by remember { mutableStateOf(false) }
    var imageRequestForFullscreen by remember { mutableStateOf<ImageRequest?>(null) }

    LaunchedEffect(timestampKey, context) {
        if (timestampKey != null) {
            Log.d("FullscreenImageScreen", "[DEBUG] Received timestampKey: $timestampKey")
            val wallpaperSource = SettingsManager.currentWallpaperSource
            val imageUrl = FrottageApiService.getWallpaperUrl(context, timestampKey)
            Log.d("FullscreenImageScreen", "[DEBUG] Derived imageUrl: $imageUrl for timestampKey: $timestampKey")
            val targetKey = FrottageApiService.getFrottageTargetKey(context)
            imageRequestForFullscreen =
                wallpaperSource.schedule.imageRequest(
                    imageUrl,
                    context,
                    timestampKey,
                    targetKey,
                )
            Log.d("FullscreenImageScreen", "[DEBUG] Created imageRequestForFullscreen: $imageRequestForFullscreen")
        } else {
            Log.w("FullscreenImageScreen", "[DEBUG] timestampKey is null. Cannot load image.")
            imageRequestForFullscreen = null
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable {
                    if (!alreadyClicked) {
                        alreadyClicked = true
                        onClick()
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (imageRequestForFullscreen != null) {
            AsyncImage(
                model = imageRequestForFullscreen,
                contentDescription = "Fullscreen Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("Loading image or image key not available...")
            Log.d("FullscreenImageScreen", "[DEBUG] imageRequestForFullscreen is null, showing placeholder text.")
        }
    }
}
