package com.frottage

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.frottage.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.frottage.ui.composables.StarRatingBar
import com.frottage.ui.composables.NextUpdateTime
import com.frottage.ui.screens.FullscreenImageScreen
import com.frottage.ui.composables.WorkInfoListScreen

class MainActivity :
    ComponentActivity(),
    Configuration.Provider {
    private val updateTrigger = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        observeWallpaperUpdates()

        setContent {
            val navController = rememberNavController()
            val triggerUpdate by updateTrigger.collectAsState()

            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NavHost(navController = navController, startDestination = "wallpaper") {
                        composable("wallpaper") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .safeDrawingPadding()
                                        .padding(
                                            top = 20.dp,
                                            bottom = 20.dp,
                                        ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val contextForRating = LocalContext.current
                                val scope = rememberCoroutineScope()
                                var currentImageUrl by remember { mutableStateOf<String?>(null) }
                                var currentImageUniqueId by remember { mutableStateOf<String?>(null) }

                                Preview(
                                    navController = navController,
                                    triggerUpdate = triggerUpdate,
                                    modifier = Modifier.weight(1f),
                                    onImageUrlChanged = { url -> currentImageUrl = url },
                                    onImageUniqueIdChanged = { id -> currentImageUniqueId = id }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                var currentRating by remember { mutableIntStateOf(0) }

                                LaunchedEffect(currentImageUniqueId, contextForRating) {
                                    currentImageUniqueId?.let { imageId ->
                                        if (imageId.isNotBlank()) {
                                            currentRating = RatingPersistence.loadRating(
                                                contextForRating,
                                                imageId
                                            )
                                            Log.d(
                                                "MainActivity",
                                                "Attempted to load rating for ID '$imageId', got $currentRating. Groovy!"
                                            )
                                        } else {
                                            currentRating = 0 // Reset if ID is blank
                                            Log.d(
                                                "MainActivity",
                                                "Image ID is blank. Setting rating to 0. Not very frottage."
                                            )
                                        }
                                    } ?: run {
                                        currentRating = 0 // Reset if ID is null
                                        Log.d(
                                            "MainActivity",
                                            "Image ID is null. Setting rating to 0. A blank canvas for frottage!"
                                        )
                                    }
                                }

                                StarRatingBar(
                                    rating = currentRating,
                                    onRatingChanged = { newRating ->
                                        currentRating = newRating // Update UI immediately
                                        val targetKeyVal = getFrottageTargetKey(contextForRating)
                                        currentImageUniqueId?.let { imageId ->
                                            if (imageId.isNotBlank()) {
                                                scope.launch {
                                                    RatingPersistence.saveRating(
                                                        contextForRating,
                                                        imageId,
                                                        newRating
                                                    )
                                                    // Still submit to the backend as before
                                                    submitRating(
                                                        contextForRating,
                                                        newRating,
                                                        targetKeyVal
                                                    )
                                                }
                                            } else {
                                                Log.w(
                                                    "MainActivity",
                                                    "Frottage Alert: Cannot save rating locally, imageUniqueId is blank! Will still try to submit to backend for '$targetKeyVal'."
                                                )
                                                scope.launch { // Attempt backend submission even if local save key is bad
                                                    submitRating(
                                                        contextForRating,
                                                        newRating,
                                                        targetKeyVal
                                                    )
                                                }
                                            }
                                        } ?: run {
                                            Log.w(
                                                "MainActivity",
                                                "Frottage Alert: Cannot save rating locally, imageUniqueId is null! Will still try to submit to backend for '$targetKeyVal'."
                                            )
                                            scope.launch { // Attempt backend submission even if local save key is bad
                                                submitRating(
                                                    contextForRating,
                                                    newRating,
                                                    targetKeyVal
                                                )
                                            }
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.width(300.dp),
                                ) {

                                    Column(
                                        modifier = Modifier
                                            .padding(
                                                start = 10.dp,
                                                top = 18.dp,
                                                end = 20.dp,
                                                bottom = 8.dp,
                                            )

                                    ) {
                                        Text(
                                            text = "Wallpaper Settings",
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                            ),
                                            modifier = Modifier.padding(
                                                start = 12.dp,
                                                bottom = 10.dp,
                                            )
                                        )
                                        lockScreenEnableCheckbox()
                                        homeScreenEnableCheckbox()
                                        homeScreenBlurCheckbox()
                                    }

                                }


                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.width(300.dp),
                                ) {

                                    Column(
                                        modifier = Modifier.padding(
                                            start = 10.dp,
                                            top = 18.dp,
                                            end = 20.dp,
                                            bottom = 8.dp,
                                        )
                                    )
                                    {
                                        Text(
                                            text = "Schedule",
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                            ),
                                            modifier = Modifier.padding(
                                                start = 12.dp,
                                                bottom = 10.dp,
                                            )
                                        )
                                        NextUpdateTime(
                                            key = triggerUpdate,
                                            navController = navController,
                                            modifier = Modifier.padding(
                                                start = 12.dp
                                            ),
                                        )

                                        ScheduleSwitch(
                                            triggerUpdate,
                                            modifier = Modifier.padding(
                                                start = 12.dp
                                            ),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                SetWallpaperButton()


                            }
                        }
                        composable("fullscreen") {
                            FullscreenImageScreen(onClick = {
                                navController.popBackStack()
                            })
                        }
                        composable("logscreen") {
                            LogFileView(onClick = {
                                navController.popBackStack()
                            })
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetWallpaperButton() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(false) }

        Button(
            modifier = Modifier.width(300.dp),
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        WallpaperSetter.setWallpaper(context)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Error: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Setting Wallpaper...",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                )
            } else {
                Text(
                    "Set Wallpaper",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                )
            }
        }
    }

    @Composable
    private fun Preview(
        navController: NavHostController,
        triggerUpdate: Int,
        modifier: Modifier,
        onImageUrlChanged: (String?) -> Unit,
        onImageUniqueIdChanged: (String?) -> Unit,
    ) {
        key(triggerUpdate) {
            val context = LocalContext.current
            val wallpaperSource =
                SettingsManager.currentWallpaperSource

            LaunchedEffect(wallpaperSource, context) { // Re-calculate if source or context changes
                val url = wallpaperSource.lockScreen?.url(context)
                onImageUrlChanged(url) // Keep for any existing dependencies

                url?.let { lockScreenUrlValue ->
                    val now = ZonedDateTime.now(ZoneId.of("UTC"))
                    val imageRequest = wallpaperSource.schedule.imageRequest(
                        lockScreenUrlValue,
                        now,
                        context
                    )
                    onImageUniqueIdChanged(imageRequest.diskCacheKey)
                } ?: run {
                    onImageUniqueIdChanged(null) // No URL, so no unique ID
                }
            }

            wallpaperSource.lockScreen?.let {
                val lockScreenUrl =
                    it.url(context) // This will be the same as currentImageUrl if lockScreen is not null
                val now = ZonedDateTime.now(ZoneId.of("UTC"))
                val imageRequest =
                    wallpaperSource.schedule.imageRequest(
                        lockScreenUrl,
                        now,
                        context,
                    )
                // The LaunchedEffect above handles calling onImageUniqueIdChanged.
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Current Lock Screen Wallpaper",
                    modifier =
                        modifier
                            .clip(shape = RoundedCornerShape(16.dp))
                            .clickable(onClick = {
                                navController.navigate("fullscreen")
                            }),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }

    @Composable
    private fun ScheduleSwitch(triggerUpdate: Int, modifier: Modifier) {
        val context = LocalContext.current
        var isScheduleEnabled by remember {
            mutableStateOf(
                SettingsManager.getScheduleIsEnabled(context),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth(),
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
                        requestBatteryOptimizationExemption()
                        scheduleNextUpdate(context)
                    } else {
                        cancelUpdateSchedule(context)
                    }
                },
            )
        }
    }


    @Composable
    private fun homeScreenBlurCheckbox() {
        val context = LocalContext.current
        var isBlurEnabled by remember {
            mutableStateOf(
                SettingsManager.getHomeScreenBlur(context),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = isBlurEnabled,
                onCheckedChange = { enabled ->
                    isBlurEnabled = enabled
                    SettingsManager.setHomeScreenBlur(
                        context,
                        isBlurEnabled,
                    )
                },
            )
            Text("Blur Home Screen")
        }
    }

    @Composable
    private fun homeScreenEnableCheckbox() {
        val context = LocalContext.current
        var isHomeScreenEnabled by remember {
            mutableStateOf(
                SettingsManager.getHomeScreenEnable(context),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = isHomeScreenEnabled,
                onCheckedChange = { isChecked ->
                    isHomeScreenEnabled = isChecked
                    SettingsManager.setHomeScreenEnable(
                        context,
                        isHomeScreenEnabled,
                    )
                },
            )
            Text("Apply to Home Screen")
        }
    }

    @Composable
    private fun lockScreenEnableCheckbox() {
        val context = LocalContext.current
        var isLockScreenEnabled by remember {
            mutableStateOf(
                SettingsManager.getLockScreenEnable(context),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = isLockScreenEnabled,
                onCheckedChange = { isChecked ->
                    isLockScreenEnabled = isChecked
                    SettingsManager.setLockScreenEnable(
                        context,
                        isLockScreenEnabled,
                    )
                },
            )
            Text("Apply to Lock Screen")
        }
    }

    private fun handleSettingsSaved() {
        val context = applicationContext
        if (SettingsManager.getScheduleIsEnabled(context)) {
            lifecycleScope.launch {
                try {
                    WallpaperSetter.setWallpaper(context)
                    updateTrigger.update { it + 1 }
                    scheduleNextUpdate(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent =
                    Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                    }
                startActivity(intent)
            }
        }
    }

    private fun observeWallpaperUpdates() {
        lifecycleScope.launch {
            WorkManager
                .getInstance(applicationContext)
                .getWorkInfosByTagFlow("wallpaper_update")
                .collect { workInfoList ->
                    workInfoList.forEach { workInfo ->
                        if (workInfo.state == WorkInfo.State.ENQUEUED) {
                            updateTrigger.update { it + 1 }
                        }
                    }
                }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Companion.workManagerConfiguration

    companion object {
        val workManagerConfiguration: Configuration
            get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()
    }
}

