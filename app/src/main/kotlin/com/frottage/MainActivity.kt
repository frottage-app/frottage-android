package com.frottage

import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                                Preview(
                                    navController = navController,
                                    triggerUpdate = triggerUpdate,
                                    modifier = Modifier.weight(1f),
                                    onImageUrlChanged = { url -> currentImageUrl = url }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                var currentRating by remember { mutableIntStateOf(0) }
                                StarRatingBar(
                                    rating = currentRating,
                                    onRatingChanged = { newRating ->
                                        currentRating = newRating
                                        val targetKeyVal = getFrottageTargetKey(contextForRating)
                                        scope.launch {
                                            submitRating(
                                                contextForRating,
                                                newRating,
                                                targetKeyVal
                                            )
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
    ) {
        key(triggerUpdate) {
            val context = LocalContext.current
            val wallpaperSource =
                SettingsManager.currentWallpaperSource

            LaunchedEffect(wallpaperSource, context) { // Re-calculate if source or context changes
                val url = wallpaperSource.lockScreen?.url(context)
                onImageUrlChanged(url)
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

@Composable
fun NextUpdateTime(key: Any? = null, navController: NavHostController, modifier: Modifier) {
    val now = ZonedDateTime.now(ZoneId.of("UTC"))
    val nextUpdateTime = SettingsManager.currentWallpaperSource.schedule.nextUpdateTime(now)
    val localNextUpdateTime = nextUpdateTime.withZoneSameInstant(ZoneId.systemDefault())
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    val formattedNextUpdateTime = localNextUpdateTime.format(timeFormat)

    var tapCount by remember { mutableStateOf(0) }

    Text(
        text = "Next image: $formattedNextUpdateTime",
        modifier = modifier.clickable {
            tapCount++
            if (tapCount >= 7) {
                navController.navigate("logscreen")
                tapCount = 0
            }
        }
    )
}

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

@Composable
fun WorkInfoListScreen() {
    val context = LocalContext.current
    val workManager = WorkManager.getInstance(context)
    val workInfosFlow: Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkLiveData("wallpaper_update").asFlow()
    val workInfos by workInfosFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    WorkInfoList(workInfos)
}

// Composable to display a list of WorkInfo
@Composable
fun WorkInfoList(workInfos: List<WorkInfo>) {
    LazyColumn {
        items(workInfos) { workInfo ->
            WorkInfoItem(workInfo)
        }
    }
}

// Composable to display a single WorkInfo item
@Composable
fun WorkInfoItem(workInfo: WorkInfo) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(text = "ID: ${workInfo.id}")
        Text(text = "State: ${workInfo.state}")
        Text(text = "Tags: ${workInfo.tags.joinToString()}")
    }
}

@Composable
fun StarRatingBar(
    modifier: Modifier = Modifier,
    maxStars: Int = 5,
    rating: Int,
    onRatingChanged: (Int) -> Unit
) {
    Row(modifier = modifier) {
        for (starIndex in 1..maxStars) {
            IconButton(onClick = { onRatingChanged(starIndex) }) {
                Icon(
                    imageVector = if (starIndex <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (starIndex <= rating) "Filled Star $starIndex" else "Empty Star $starIndex",
                    tint = if (starIndex <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.5f
                    )
                )
            }
        }
    }
}
