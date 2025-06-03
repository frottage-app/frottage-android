package com.frottage

import android.Manifest
import android.content.ContentValues
import android.content.Intent
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                                    onImageUrlChange = { url -> currentImageUrl = url },
                                    onImageUniqueIdChange = { id -> currentImageUniqueId = id },
                                    modifier = Modifier.weight(1f),
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                var currentRating by remember { mutableIntStateOf(0) }

                                LaunchedEffect(currentImageUniqueId, contextForRating) {
                                    currentImageUniqueId?.let { imageId ->
                                        if (imageId.isNotBlank()) {
                                            currentRating =
                                                RatingPersistence.loadRating(
                                                    contextForRating,
                                                    imageId,
                                                )
                                            Log.d(
                                                "MainActivity",
                                                "Attempted to load rating for ID '$imageId', got $currentRating. Groovy!",
                                            )
                                        } else {
                                            currentRating = 0 // Reset if ID is blank
                                            Log.d(
                                                "MainActivity",
                                                "Image ID is blank. Setting rating to 0. Not very frottage.",
                                            )
                                        }
                                    } ?: run {
                                        currentRating = 0 // Reset if ID is null
                                        Log.d(
                                            "MainActivity",
                                            "Image ID is null. Setting rating to 0. A blank canvas for frottage!",
                                        )
                                    }
                                }

                                StarRatingBar(
                                    rating = currentRating,
                                    onRatingChange = { newRating ->
                                        currentRating = newRating // Update UI immediately
                                        val targetKeyVal = getFrottageTargetKey(contextForRating)
                                        currentImageUniqueId?.let { imageId ->
                                            if (imageId.isNotBlank()) {
                                                scope.launch {
                                                    RatingPersistence.saveRating(
                                                        contextForRating,
                                                        imageId,
                                                        newRating,
                                                    )
                                                    submitRating(
                                                        contextForRating,
                                                        newRating,
                                                        imageId,
                                                    )
                                                }
                                            } else {
                                                Log.w(
                                                    "MainActivity",
                                                    "Frottage Alert: Cannot save rating locally, imageUniqueId (image_id) is blank!",
                                                )
                                                scope.launch {
                                                    // Attempt backend submission even if local save key is bad
                                                    submitRating(
                                                        contextForRating,
                                                        newRating,
                                                        targetKeyVal,
                                                    )
                                                }
                                            }
                                        } ?: run {
                                            Log.w(
                                                "MainActivity",
                                                "Frottage Alert: Cannot save rating locally, imageUniqueId (image_id) is null!",
                                            )
                                            scope.launch {
                                                // Attempt backend submission even if local save key is bad
                                                submitRating(
                                                    contextForRating,
                                                    newRating,
                                                    targetKeyVal,
                                                )
                                            }
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
                                            key = triggerUpdate,
                                            navController = navController,
                                            modifier =
                                                Modifier.padding(
                                                    start = 12.dp,
                                                ),
                                        )

                                        ScheduleSwitch(
                                            triggerUpdate,
                                            modifier =
                                                Modifier.padding(
                                                    start = 12.dp,
                                                ),
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
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                    )
                                    currentImageUrl?.let { imageUrl ->
                                        SaveWallpaperButton(
                                            imageUrl = imageUrl,
                                            imageUniqueId = currentImageUniqueId,
                                        )
                                    }
                                }
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
    private fun SetWallpaperButton(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(false) }

        Button(
            modifier = modifier,
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        WallpaperSetter.setWallpaper(context)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
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
        triggerUpdate: Int,
        onImageUrlChange: (String?) -> Unit,
        onImageUniqueIdChange: (String?) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        key(triggerUpdate) {
            val context = LocalContext.current
            val wallpaperSource =
                SettingsManager.currentWallpaperSource

            LaunchedEffect(wallpaperSource, context, onImageUrlChange, onImageUniqueIdChange) {
                val imageUrl = wallpaperSource.lockScreen?.url(context)
                onImageUrlChange(imageUrl)

                if (imageUrl != null) {
                    val currentTargetKey = getFrottageTargetKey(context)
                    Log.d(
                        "MainActivity.Preview",
                        "Fetching image ID for targetKey: $currentTargetKey",
                    )
                    val imageId =
                        ImageMetadataService.fetchAndParseImageId(
                            context,
                            wallpaperSource.schedule,
                            currentTargetKey,
                        )
                    Log.d("MainActivity.Preview", "Image ID fetched: $imageId")
                    onImageUniqueIdChange(imageId)
                } else {
                    Log.d("MainActivity.Preview", "Image URL is null, setting imageId to null")
                    onImageUniqueIdChange(null)
                }
            }

            wallpaperSource.lockScreen?.let {
                val lockScreenUrl =
                    it.url(context)
                val imageRequest =
                    wallpaperSource.schedule.imageRequest(
                        lockScreenUrl,
                        ZonedDateTime.now(ZoneId.of("UTC")),
                        context,
                    )
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
    private fun ScheduleSwitch(
        triggerUpdate: Int,
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        var isScheduleEnabled by remember {
            mutableStateOf(
                SettingsManager.getScheduleIsEnabled(context),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier =
                modifier
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

    @Composable
    private fun SaveWallpaperButton(
        imageUrl: String,
        imageUniqueId: String?,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(false) }

        val permissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                if (isGranted) {
                    Log.d("SaveWallpaper", "WRITE_EXTERNAL_STORAGE permission granted after request. Groovy!")
                    // isLoading is already true from the onClick that launched the permission request
                    scope.launch { performSaveOperation(context, imageUrl, imageUniqueId) { isLoading = false } }
                } else {
                    Log.w("SaveWallpaper", "WRITE_EXTERNAL_STORAGE permission denied. Not very frottage.")
                    Toast.makeText(context, "Storage permission denied. Cannot save image.", Toast.LENGTH_SHORT).show()
                    isLoading = false // Reset isLoading if permission is denied by user
                }
            }

        IconButton(
            onClick = {
                isLoading = true // Set loading true at the beginning of any save attempt
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // Android 9 (API 28) or older
                    when (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        PackageManager.PERMISSION_GRANTED -> {
                            Log.d("SaveWallpaper", "Permission already granted for old Android. Groovy!")
                            scope.launch { performSaveOperation(context, imageUrl, imageUniqueId) { isLoading = false } }
                        }
                        else -> {
                            Log.d("SaveWallpaper", "Requesting permission for old Android.")
                            // isLoading is already true
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                } else {
                    // For Android 10+ (API 29+), MediaStore handles it for app-owned content in public directories
                    Log.d("SaveWallpaper", "Modern Android (API 29+), proceeding with save directly.")
                    scope.launch { performSaveOperation(context, imageUrl, imageUniqueId) { isLoading = false } }
                }
            },
            enabled = !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Download, // Ensure this import is present
                    contentDescription = "Save Wallpaper",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    // Renamed from saveImageAction and added onComplete lambda parameter
    private suspend fun performSaveOperation(
        context: android.content.Context,
        imageUrl: String,
        imageUniqueId: String?,
        onComplete: () -> Unit, // Lambda to call in finally block
    ) {
        try {
            val imageLoader = ImageLoader(context)
            val request =
                ImageRequest
                    .Builder(context)
                    .data(imageUrl)
                    .allowHardware(false) // Important for direct bitmap access
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
}
