package com.frottage

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.frottage.theme.AppTheme
import com.frottage.ui.composables.NextUpdateTime
import com.frottage.ui.composables.StarRatingBar
import com.frottage.ui.screens.FullscreenImageScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

class MainActivity :
    ComponentActivity(),
    Configuration.Provider {
    @Suppress("ktlint:standard:backing-property-naming")
    private val _updateTriggerFromWorkManager = MutableStateFlow(0)
    private val manualSetWallpaperWorkName = "manual_set_wallpaper_work_tag"

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Determine and set screen orientation based on device type
        if (isTablet(applicationContext)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        observeWallpaperUpdates()

        setContent {
            val navController = rememberNavController()

            // Collect states from ViewModel
            val currentTimestampKey by viewModel.currentTimestampKey.collectAsState()
            val currentlyDisplayedImageId by viewModel.currentlyDisplayedImageId.collectAsState()
            val displayedRating by viewModel.displayedRating.collectAsState()
            val isRatingEnabled by viewModel.isRatingEnabled.collectAsState()
            val imageRequestForPreview by viewModel.imageRequestForPreview.collectAsState()

            // Effect to react to WorkManager updates and trigger ViewModel refresh
            LaunchedEffect(Unit) {
                // Observe WorkManager updates once
                _updateTriggerFromWorkManager.collect {
                    // This value itself isn't directly used by ViewModel's internal trigger,
                    // but its change signifies a need to potentially re-evaluate.
                    Log.d("MainActivity", "WorkManager update detected, forcing ViewModel refresh.")
                    viewModel.forceUIRefresh()
                }
            }

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
            val context = LocalContext.current

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

    private fun observeWallpaperUpdates() {
        lifecycleScope.launch {
            WorkManager
                .getInstance(applicationContext)
                .getWorkInfosByTagFlow("wallpaper_update")
                .collect { workInfoList ->
                    workInfoList.forEach { workInfo ->
                        if (SettingsManager.getScheduleIsEnabled(applicationContext)) {
                            if (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING) {
                                Log.d(
                                    "MainActivity",
                                    "Scheduled wallpaper_update work state changed: ${workInfo.state} AND schedule is ENABLED, triggering UI refresh via ViewModel",
                                )
                                _updateTriggerFromWorkManager.update { it + 1 }
                            } else {
                                Log.d(
                                    "MainActivity",
                                    "Scheduled wallpaper_update work state is ${workInfo.state} but schedule is ENABLED - not triggering an ENQUEUED/RUNNING update.",
                                )
                            }
                        } else {
                            Log.d(
                                "MainActivity",
                                "Scheduled wallpaper_update work state changed: ${workInfo.state} BUT schedule is DISABLED, NOT triggering UI refresh via ViewModel",
                            )
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

    @Composable
    private fun SaveWallpaperButton(
        imageUrl: String,
        imageUniqueId: String?,
    ) {
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(false) }

        val permissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                if (isGranted) {
                    Log.d("SaveWallpaper", "WRITE_EXTERNAL_STORAGE permission granted after request. Groovy!")
                    lifecycleScope.launch { performSaveOperation(context, imageUrl, imageUniqueId) { isLoading = false } }
                } else {
                    Log.w("SaveWallpaper", "WRITE_EXTERNAL_STORAGE permission denied. Not very frottage.")
                    Toast.makeText(context, "Storage permission denied. Cannot save image.", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
            }

        IconButton(
            onClick = {
                isLoading = true
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    when (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        PackageManager.PERMISSION_GRANTED -> {
                            Log.d("SaveWallpaper", "Permission already granted for old Android. Groovy!")
                            lifecycleScope.launch { performSaveOperation(context, imageUrl, imageUniqueId) { isLoading = false } }
                        }
                        else -> {
                            Log.d("SaveWallpaper", "Requesting permission for old Android.")
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                } else {
                    Log.d("SaveWallpaper", "Modern Android (API 29+), proceeding with save directly.")
                    lifecycleScope.launch { performSaveOperation(context, imageUrl, imageUniqueId) { isLoading = false } }
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

    private suspend fun performSaveOperation(
        context: android.content.Context,
        imageUrl: String,
        imageUniqueId: String?,
        onComplete: () -> Unit,
    ) {
        try {
            val imageLoader = ImageLoader(context)
            val request =
                ImageRequest
                    .Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .build()
            val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap

            if (bitmap != null) {
                val displayName = "frottage_${imageUniqueId ?: System.currentTimeMillis()}.jpg"
                val mimeType = "image/jpeg"

                val contentValues =
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Frottage")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        } else {
                            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            val frottageDir = java.io.File(picturesDir, "Frottage")
                            if (!frottageDir.exists()) {
                                frottageDir.mkdirs()
                            }
                            val imageFile = java.io.File(frottageDir, displayName)
                            put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                        }
                    }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    withContext(Dispatchers.IO) {
                        resolver.openOutputStream(it)?.use { outStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(it, contentValues, null, null)
                    }
                    Log.i("SaveWallpaper", "Groovy! Image saved to MediaStore: $it")
                    Toast.makeText(context, "Image saved to Pictures/Frottage!", Toast.LENGTH_SHORT).show()
                } ?: throw Exception("MediaStore URI was null, frottage fail!")
            } else {
                throw Exception("Failed to load bitmap from URL.")
            }
        } catch (e: Exception) {
            Log.e("SaveWallpaper", "Frottage fail! Could not save image: ${e.message}", e)
            Toast.makeText(context, "Frottage fail! Could not save image: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            onComplete()
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
                    } else {
                        logToFile(context, "Schedule disabled. Frottage paused for now.")
                        cancelUpdateSchedule(context)
                    }
                },
            )
        }
    }
}
