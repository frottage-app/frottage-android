package com.frottage

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.Configuration
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.frottage.theme.AppTheme
import com.frottage.ui.composables.NextUpdateTime
import com.frottage.ui.composables.StarRatingBar
import com.frottage.ui.screens.FullscreenImageScreen
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

class MainActivity :
    ComponentActivity(),
    Configuration.Provider {
    private val manualSetWallpaperWorkName = "manual_set_wallpaper_work_tag"

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Determine and set screen orientation based on device type
        if (FrottageApiService.isTablet(applicationContext)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContent {
            val navController = rememberNavController()

            // Collect states from ViewModel
            val currentTimestampKey by viewModel.currentTimestampKey.collectAsState()
            val currentlyDisplayedImageId by viewModel.currentlyDisplayedImageId.collectAsState()
            val displayedRating by viewModel.displayedRating.collectAsState()
            val isRatingEnabled by viewModel.isRatingEnabled.collectAsState()
            val imageRequestForPreview by viewModel.imageRequestForPreview.collectAsState()

            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NavHost(navController = navController, startDestination = "wallpaper") {
                        composable("wallpaper") {
                            val context = LocalContext.current
                            // val workManager = WorkManager.getInstance(context) // No longer needed for manual set

                            // Observe the WorkInfo for the manual set wallpaper work -- REMOVE THIS
                            // val manualSetWorkInfoList: List<WorkInfo> by workManager
                            //     .getWorkInfosForUniqueWorkLiveData(manualSetWallpaperWorkName)
                            //     .asFlow()
                            //     .collectAsStateWithLifecycle(initialValue = emptyList())
                            // val actualWorkInfo: WorkInfo? = manualSetWorkInfoList.firstOrNull()

                            // Observe ViewModel state for manual set operation
                            val isManualSetInProgress by viewModel.isManualSetInProgress.collectAsState()
                            val manualSetResultMessage by viewModel.manualSetResultMessage.collectAsState()

                            LaunchedEffect(manualSetResultMessage) {
                                manualSetResultMessage?.let {
                                    Toast
                                        .makeText(
                                            context,
                                            it,
                                            if (it.startsWith("Frottage fail")) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                                        ).show()
                                    viewModel.clearManualSetResultMessage() // Clear after showing
                                }
                            }

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
                                Preview(
                                    navController = navController,
                                    imageRequest = imageRequestForPreview,
                                    timestampKeyForFullscreen = currentTimestampKey,
                                    modifier = Modifier.weight(1f),
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                StarRatingBar(
                                    rating = displayedRating,
                                    enabled = isRatingEnabled,
                                    onRatingChange = { newRating ->
                                        if (isRatingEnabled && currentlyDisplayedImageId != null) {
                                            viewModel.handleRatingChange(newRating, currentlyDisplayedImageId, context)
                                        } else {
                                            Log.w(
                                                "MainActivity",
                                                "StarRatingBar onRatingChange: Rating not enabled or imageId is null. isRatingEnabled: $isRatingEnabled, imageId: $currentlyDisplayedImageId",
                                            )
                                        }
                                    },
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.width(300.dp),
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .padding(
                                                    start = 10.dp,
                                                    top = 18.dp,
                                                    end = 20.dp,
                                                    bottom = 8.dp,
                                                ),
                                    ) {
                                        Text(
                                            text = "Wallpaper Settings",
                                            style =
                                                androidx.compose.ui.text.TextStyle(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp,
                                                ),
                                            modifier =
                                                Modifier.padding(
                                                    start = 12.dp,
                                                    bottom = 10.dp,
                                                ),
                                        )
                                        LockScreenEnableCheckbox()
                                        HomeScreenEnableCheckbox()
                                        HomeScreenBlurCheckbox()
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.width(300.dp),
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
                                                androidx.compose.ui.text.TextStyle(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp,
                                                ),
                                            modifier =
                                                Modifier.padding(
                                                    start = 12.dp,
                                                    bottom = 10.dp,
                                                ),
                                        )
                                        NextUpdateTime(
                                            navController = navController,
                                            key = currentTimestampKey,
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                        ScheduleSwitch(
                                            modifier = Modifier.padding(start = 12.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.width(300.dp),
                                ) {
                                    SetWallpaperButton(
                                        currentTimestampKey = currentTimestampKey,
                                        // workInfo = actualWorkInfo, // No longer pass WorkInfo
                                        isLoading = isManualSetInProgress, // Pass ViewModel's loading state
                                        onClick = {
                                            if (currentTimestampKey != null) {
                                                viewModel.manuallySetCurrentWallpaper() // Call ViewModel function
                                            } else {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        "Cannot set wallpaper: timestamp key is missing",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                Log.w("SetWallpaperButton", "currentTimestampKey is null, cannot trigger manual set")
                                            }
                                        },
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )
                                    val imageUrlForSave =
                                        currentTimestampKey?.let { tsKey ->
                                            SettingsManager.currentWallpaperSource.imageSetting.url
                                                .invoke(context, tsKey)
                                        }

                                    if (imageUrlForSave != null) {
                                        SaveWallpaperButton(
                                            imageUrl = imageUrlForSave,
                                            imageUniqueId = currentlyDisplayedImageId,
                                        )
                                    } else {
                                        Log.w(
                                            "MainActivity",
                                            "SaveWallpaperButton: imageUrlForSave is null because currentTimestampKey is null.",
                                        )
                                    }
                                }
                            }
                        }
                        composable(
                            route = "fullscreen/{timestampKey}",
                            arguments =
                                listOf(
                                    navArgument("timestampKey") {
                                        type = NavType.StringType
                                        nullable = true
                                    },
                                ),
                        ) { backStackEntry ->
                            val timestampKey = backStackEntry.arguments?.getString("timestampKey")
                            FullscreenImageScreen(
                                timestampKey = timestampKey,
                                onClick = {
                                    navController.popBackStack()
                                },
                            )
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
    private fun SetWallpaperButton(
        currentTimestampKey: String?, // Keep this to enable/disable button
        // workInfo: WorkInfo?, // Removed
        isLoading: Boolean, // Added
        onClick: () -> Unit, // Added
        // viewModel: MainActivityViewModel, // No longer needed directly here if onClick calls ViewModel
        modifier: Modifier = Modifier,
    ) {
        Log.d("SetWallpaperButton", "Recomposing. isLoading (from ViewModel): $isLoading, currentTimestampKey: $currentTimestampKey")

        // val context = LocalContext.current // Context for toast is now handled by the LaunchedEffect observing ViewModel
        // val isLoading = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state == WorkInfo.State.ENQUEUED // isLoading now passed as parameter

        // Removed local state and LaunchedEffects for workInfo, showSuccessToast, showFailureToast

        Button(
            modifier = modifier,
            onClick = onClick, // Use passed onClick
            enabled = !isLoading && currentTimestampKey != null,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Setting Wallpaper...",
                    style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                )
            } else {
                Text(
                    "Set Wallpaper",
                    style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                )
            }
        }
    }

    @Composable
    private fun Preview(
        navController: NavHostController,
        imageRequest: ImageRequest?,
        timestampKeyForFullscreen: String?,
        modifier: Modifier = Modifier,
    ) {
        key(imageRequest) {
            Log.d(
                "MainActivity",
                "[DEBUG] Preview Composable: Received imageRequest: $imageRequest, timestampKeyForFullscreen: $timestampKeyForFullscreen",
            )
            @Suppress("SENSELESS_COMPARISON")
            if (imageRequest != null) {
                Log.d("MainActivity", "[DEBUG] Preview Composable: imageRequest is NOT NULL, attempting to load AsyncImage.")
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Current Lock Screen Wallpaper",
                    modifier =
                        modifier
                            .clip(shape = RoundedCornerShape(16.dp))
                            .clickable(onClick = {
                                if (timestampKeyForFullscreen != null) {
                                    navController.navigate("fullscreen/$timestampKeyForFullscreen")
                                } else {
                                    Log.w("Preview", "timestampKeyForFullscreen is null, cannot navigate to fullscreen.")
                                }
                            }),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Spacer(modifier = modifier.clip(shape = RoundedCornerShape(16.dp)))
                Log.d("Preview", "imageRequest is null, showing Spacer.")
            }
        }
    }

    @Composable
    private fun HomeScreenBlurCheckbox() {
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
    private fun HomeScreenEnableCheckbox() {
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
    private fun LockScreenEnableCheckbox() {
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

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called, forcing ViewModel refresh for live preview.")
        viewModel.forceUIRefresh()
    }

    override val workManagerConfiguration: Configuration
        get() = Companion.workManagerConfiguration

    companion object {
        val workManagerConfiguration: Configuration
            get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()
    }

    @Composable
    private fun SaveWallpaperButton(
        imageUrl: String,
        imageUniqueId: String?,
    ) {
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope() // Added for launching coroutine from permission result

        val permissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                isLoading = false // Reset loading state after permission dialog closes
                if (isGranted) {
                    Log.d("SaveWallpaper", "WRITE_EXTERNAL_STORAGE permission granted by user. Groovy! Retrying save.")
                    coroutineScope.launch {
                        // Use coroutineScope here
                        ImageSaver.saveImageRequiringPermissions(
                            context = context,
                            imageUrl = imageUrl,
                            imageUniqueId = imageUniqueId,
                            onRequestPermission = { /* Should not be called again here if permission was just granted */ },
                            onSaveAttemptFinalized = { success, message ->
                                isLoading = false
                                message?.let {
                                    Toast.makeText(context, it, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                } else {
                    Log.w("SaveWallpaper", "WRITE_EXTERNAL_STORAGE permission denied by user. Not very frottage.")
                    Toast.makeText(context, "Storage permission is needed to save the image. Frottage halted.", Toast.LENGTH_LONG).show()
                }
            }

        IconButton(
            onClick = {
                isLoading = true
                Log.d("SaveWallpaper", "Save button clicked. isLoading set to true.")
                coroutineScope.launch {
                    // Use coroutineScope for the initial call as well
                    ImageSaver.saveImageRequiringPermissions(
                        context = context,
                        imageUrl = imageUrl,
                        imageUniqueId = imageUniqueId,
                        onRequestPermission = {
                            Log.d("SaveWallpaper", "Requesting permission from ImageSaver callback.")
                            // isLoading is already true, no need to set it again before launching permission dialog
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            // Note: isLoading will be reset by permissionLauncher's callback or onSaveAttemptFinalized if permission wasn't needed
                        },
                        onSaveAttemptFinalized = { success, message ->
                            isLoading = false
                            Log.d("SaveWallpaper", "onSaveAttemptFinalized: success=$success, message=$message, isLoading set to false.")
                            message?.let {
                                Toast.makeText(context, it, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            },
            enabled = !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Save Wallpaper",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    @Composable
    private fun NextUpdateTime(
        navController: NavHostController,
        modifier: Modifier = Modifier,
        key: Any? = null,
    ) {
        val context = LocalContext.current
        var tapCount by remember { mutableIntStateOf(0) }

        val now = ZonedDateTime.now(ZoneId.of("UTC"))
        val nextUpdateTime = SettingsManager.currentWallpaperSource.schedule.nextUpdateTime(now)
        val localNextUpdateTime = nextUpdateTime.withZoneSameInstant(ZoneId.systemDefault())
        val timeFormat =
            java.time.format.DateTimeFormatter
                .ofPattern("HH:mm", java.util.Locale.getDefault())
        val formattedNextUpdateTime = localNextUpdateTime.format(timeFormat)

        Text(
            text = "Next image: $formattedNextUpdateTime",
            modifier =
                modifier.clickable {
                    tapCount++
                    if (tapCount >= 7) {
                        try {
                            navController.navigate("logscreen")
                        } catch (e: IllegalStateException) {
                            Log.e("NextUpdateTime", "Navigation failed, make sure 'logscreen' is a valid destination.", e)
                        }
                        tapCount = 0
                    }
                },
        )
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
            androidx.compose.material3.Switch(
                checked = isScheduleEnabled,
                onCheckedChange = { enabled ->
                    isScheduleEnabled = enabled
                    SettingsManager.setScheduleIsEnabled(
                        context,
                        isScheduleEnabled,
                    )
                    if (enabled) {
                        logToFile(context, "Schedule enabled. Let the frottage flow!")
                        requestBatteryOptimizationExemption()
                        scheduleNextUpdate(context)
                        AnalyticsService.trackEvent(context, "enable_schedule")
                    } else {
                        logToFile(context, "Schedule disabled. Frottage paused for now.")
                        cancelUpdateSchedule(context)
                        AnalyticsService.trackEvent(context, "disable_schedule")
                    }
                },
            )
        }
    }
}
