package com.frottage

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.frottage.ui.composables.PromptText
import com.frottage.ui.composables.ScheduleSettingsCard
import com.frottage.ui.composables.StarRatingBar
import com.frottage.ui.composables.WallpaperSettingsCard
import com.frottage.ui.screens.FullscreenImageScreen
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch

class MainActivity :
    ComponentActivity(),
    Configuration.Provider {
    private val manualSetWallpaperWorkName = "manual_set_wallpaper_work_tag"

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Analytics: Track app_launched_first_time
        if (!SettingsManager.getFirstLaunchEventSent(applicationContext)) {
            AnalyticsService.trackEvent(applicationContext, "app_launched_first_time")
            SettingsManager.setFirstLaunchEventSent(applicationContext, true)
            Log.i("MainActivity", "Groovy! First launch event sent and flag set.")
        }

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
            val currentImageDetails by viewModel.currentImageDetails.collectAsState()
            val displayedRating by viewModel.displayedRating.collectAsState()
            val isRatingEnabled by viewModel.isRatingEnabled.collectAsState()
            val imageRequestForPreview by viewModel.imageRequestForPreview.collectAsState()
            val showInAppReviewRequest by viewModel.showInAppReviewRequest.collectAsState()

            var showRatingInfoDialog by remember { mutableStateOf(false) }

            // Handle In-App Review Request
            LaunchedEffect(showInAppReviewRequest) {
                if (showInAppReviewRequest) {
                    Log.i("MainActivity", "Groovy! Time to ask for a review.")
                    val reviewManager = ReviewManagerFactory.create(applicationContext)
                    val request = reviewManager.requestReviewFlow()
                    request.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val reviewInfo = task.result
                            Log.d("MainActivity", "ReviewInfo obtained successfully. Proceeding to launch review flow.")
                            val flow = reviewManager.launchReviewFlow(this@MainActivity, reviewInfo)
                            flow.addOnCompleteListener { _ ->
                                // The review flow has finished. The API does not indicate whether the user
                                // reviewed or not, or even whether the dialog was shown. Here we just
                                // know the process is done.
                                Log.i(
                                    "MainActivity",
                                    "In-app review flow finished. User may or may not have reviewed. Total frottage either way!",
                                )
                            }
                        } else {
                            // There was some problem, continue regardless of the result.
                            Log.w("MainActivity", "Frottage hiccup: Could not get ReviewInfo: ${task.exception?.message}")
                        }
                        // Reset the signal in ViewModel after attempting to show the review
                        viewModel.inAppReviewRequested()
                    }
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
                            // val workManager = WorkManager.getInstance(context) // No longer
                            // needed for manual set

                            // Observe the WorkInfo for the manual set wallpaper work -- REMOVE THIS
                            // val manualSetWorkInfoList: List<WorkInfo> by workManager
                            //     .getWorkInfosForUniqueWorkLiveData(manualSetWallpaperWorkName)
                            //     .asFlow()
                            //     .collectAsStateWithLifecycle(initialValue = emptyList())
                            // val actualWorkInfo: WorkInfo? = manualSetWorkInfoList.firstOrNull()

                            // Observe ViewModel state for manual set operation
                            val isManualSetInProgress by
                                viewModel.isManualSetInProgress.collectAsState()
                            val manualSetResultMessage by
                                viewModel.manualSetResultMessage.collectAsState()

                            LaunchedEffect(manualSetResultMessage) {
                                manualSetResultMessage?.let {
                                    Toast
                                        .makeText(
                                            context,
                                            it,
                                            if (it.startsWith("Frottage fail")) {
                                                Toast.LENGTH_LONG
                                            } else {
                                                Toast.LENGTH_SHORT
                                            },
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

                                Spacer(modifier = Modifier.height(12.dp))

                                PromptText(prompt = currentImageDetails?.purePrompt)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StarRatingBar(
                                        rating = displayedRating,
                                        enabled = isRatingEnabled,
                                        onRatingChange = { newRating ->
                                            if (isRatingEnabled &&
                                                currentImageDetails?.imageId != null
                                            ) {
                                                viewModel.handleRatingChange(
                                                    newRating,
                                                    currentImageDetails?.imageId,
                                                    context,
                                                )
                                            } else {
                                                Log.w(
                                                    "MainActivity",
                                                    "StarRatingBar onRatingChange: Rating not enabled or imageId is null. isRatingEnabled: $isRatingEnabled, imageId: ${currentImageDetails?.imageId}",
                                                )
                                            }
                                        },
                                    )
                                    IconButton(onClick = {
                                        AnalyticsService.trackEvent(context, "rating_info_opened")
                                        showRatingInfoDialog = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = "Rating Information",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                WallpaperSettingsCard()

                                Spacer(modifier = Modifier.height(12.dp))

                                ScheduleSettingsCard(navController = navController, currentTimestampKey = currentTimestampKey)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.width(300.dp),
                                ) {
                                    SetWallpaperButton(
                                        currentTimestampKey = currentTimestampKey,
                                        // workInfo = actualWorkInfo, // No longer pass WorkInfo
                                        isLoading = isManualSetInProgress, // Pass ViewModel's
                                        // loading state
                                        onClick = {
                                            if (currentTimestampKey != null) {
                                                viewModel.manuallySetCurrentWallpaper() // Call
                                                // ViewModel function
                                            } else {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        "Cannot set wallpaper: timestamp key is missing",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                Log.w(
                                                    "SetWallpaperButton",
                                                    "currentTimestampKey is null, cannot trigger manual set",
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )
                                    val imageUrlForSave =
                                        currentTimestampKey?.let { tsKey ->
                                            SettingsManager.currentWallpaperSource.imageSetting
                                                .url
                                                .invoke(context, tsKey)
                                        }

                                    if (imageUrlForSave != null) {
                                        SaveWallpaperButton(
                                            imageUrl = imageUrlForSave,
                                            imageUniqueId = currentImageDetails?.imageId,
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
                                onClick = { navController.popBackStack() },
                            )
                        }
                        composable("logscreen") {
                            LogFileView(onClick = { navController.popBackStack() })
                        }
                    }
                    if (showRatingInfoDialog) {
                        RatingInfoPopup(
                            onDismiss = { showRatingInfoDialog = false },
                        )
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
        // viewModel: MainActivityViewModel, // No longer needed directly here if onClick calls
        // ViewModel
        modifier: Modifier = Modifier,
    ) {
        Log.d(
            "SetWallpaperButton",
            "Recomposing. isLoading (from ViewModel): $isLoading, currentTimestampKey: $currentTimestampKey",
        )

        // val context = LocalContext.current // Context for toast is now handled by the
        // LaunchedEffect observing ViewModel
        // val isLoading = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state ==
        // WorkInfo.State.ENQUEUED // isLoading now passed as parameter

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
                Log.d(
                    "MainActivity",
                    "[DEBUG] Preview Composable: imageRequest is NOT NULL, attempting to load AsyncImage.",
                )
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Current Lock Screen Wallpaper",
                    modifier =
                        modifier
                            .clip(shape = RoundedCornerShape(16.dp))
                            .clickable(
                                onClick = {
                                    if (timestampKeyForFullscreen != null) {
                                        AnalyticsService.trackEvent(
                                            navController.context,
                                            "fullscreen_image_opened",
                                        )
                                        navController.navigate(
                                            "fullscreen/$timestampKeyForFullscreen",
                                        )
                                    } else {
                                        Log.w(
                                            "Preview",
                                            "timestampKeyForFullscreen is null, cannot navigate to fullscreen.",
                                        )
                                    }
                                },
                            ),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Spacer(modifier = modifier.clip(shape = RoundedCornerShape(16.dp)))
                Log.d("Preview", "imageRequest is null, showing Spacer.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called, forcing ViewModel refresh for live preview.")
        viewModel.forceUIRefresh()
        AnalyticsService.trackEvent(applicationContext, "app_resumed")
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
        val coroutineScope =
            rememberCoroutineScope() // Added for launching coroutine from permission result

        val permissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                isLoading = false // Reset loading state after permission dialog closes
                if (isGranted) {
                    Log.d(
                        "SaveWallpaper",
                        "WRITE_EXTERNAL_STORAGE permission granted by user. Groovy! Retrying save.",
                    )
                    coroutineScope.launch {
                        // Use coroutineScope here
                        ImageSaver.saveImageRequiringPermissions(
                            context = context,
                            imageUrl = imageUrl,
                            imageUniqueId = imageUniqueId,
                            onRequestPermission = {
                                // Should not be called again here if
                                // permission was just granted
                            },
                            onSaveAttemptFinalized = { success, message ->
                                isLoading = false
                                message?.let {
                                    Toast
                                        .makeText(
                                            context,
                                            it,
                                            if (success) {
                                                Toast.LENGTH_SHORT
                                            } else {
                                                Toast.LENGTH_LONG
                                            },
                                        ).show()
                                }
                            },
                        )
                    }
                } else {
                    Log.w(
                        "SaveWallpaper",
                        "WRITE_EXTERNAL_STORAGE permission denied by user. Not very frottage.",
                    )
                    Toast
                        .makeText(
                            context,
                            "Storage permission is needed to save the image. Frottage halted.",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }

        IconButton(
            onClick = {
                AnalyticsService.trackEvent(context, "save_wallpaper_clicked")
                isLoading = true
                Log.d("SaveWallpaper", "Save button clicked. isLoading set to true.")
                coroutineScope.launch {
                    // Use coroutineScope for the initial call as well
                    ImageSaver.saveImageRequiringPermissions(
                        context = context,
                        imageUrl = imageUrl,
                        imageUniqueId = imageUniqueId,
                        onRequestPermission = {
                            Log.d(
                                "SaveWallpaper",
                                "Requesting permission from ImageSaver callback.",
                            )
                            // isLoading is already true, no need to set it again before
                            // launching permission dialog
                            permissionLauncher.launch(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            )
                            // Note: isLoading will be reset by permissionLauncher's
                            // callback or onSaveAttemptFinalized if permission wasn't
                            // needed
                        },
                        onSaveAttemptFinalized = { success, message ->
                            isLoading = false
                            Log.d(
                                "SaveWallpaper",
                                "onSaveAttemptFinalized: success=$success, message=$message, isLoading set to false.",
                            )
                            message?.let {
                                Toast
                                    .makeText(
                                        context,
                                        it,
                                        if (success) {
                                            Toast.LENGTH_SHORT
                                        } else {
                                            Toast.LENGTH_LONG
                                        },
                                    ).show()
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
}

@Composable
fun RatingInfoPopup(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Star Ratings") },
        text = {
            Text(
                "Your ratings help us understand which wallpapers our community enjoys. This helps us pick even better ones in the future. All ratings are anonymous.",
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Groovy")
            }
        },
    )
}
