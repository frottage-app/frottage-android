package com.frottage

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

class MainActivityViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _currentTimestampKey = MutableStateFlow<String?>(null)
    val currentTimestampKey: StateFlow<String?> = _currentTimestampKey.asStateFlow()

    private val _currentlyDisplayedImageId = MutableStateFlow<String?>(null)
    val currentlyDisplayedImageId: StateFlow<String?> = _currentlyDisplayedImageId.asStateFlow()

    private val _displayedRating = MutableStateFlow(0)
    val displayedRating: StateFlow<Int> = _displayedRating.asStateFlow()

    private val _isRatingEnabled = MutableStateFlow(false)
    val isRatingEnabled: StateFlow<Boolean> = _isRatingEnabled.asStateFlow()

    private val _imageRequestForPreview = MutableStateFlow<ImageRequest?>(null)
    val imageRequestForPreview: StateFlow<ImageRequest?> = _imageRequestForPreview.asStateFlow()

    // States for manual wallpaper set operation
    private val _isManualSetInProgress = MutableStateFlow(false)
    val isManualSetInProgress: StateFlow<Boolean> = _isManualSetInProgress.asStateFlow()

    private val _manualSetResultMessage = MutableStateFlow<String?>(null)
    val manualSetResultMessage: StateFlow<String?> = _manualSetResultMessage.asStateFlow()

    @Suppress("ktlint:standard:backing-property-naming")
    private val _updateTrigger = MutableStateFlow(0)

    fun forceUIRefresh() { // Expose a way to trigger refresh if needed from Activity
        _updateTrigger.update { it + 1 }
    }

    init {
        Log.d("ViewModel", "ViewModel initialized")
        // Effect 0: Determine currentTimestampKey (observes internal updateTrigger)
        viewModelScope.launch {
            _updateTrigger.collect { triggerValue ->
                Log.d(
                    "ViewModel",
                    "Effect 0 (ViewModel): triggerUpdate: $triggerValue. Recalculating currentTimestampKey for live preview.",
                )
                // Hoisted common calculation
                val now = ZonedDateTime.now(ZoneId.of("UTC"))
                val currentScheduleDetails = SettingsManager.currentWallpaperSource.schedule
                val timestampKeyFromTime = currentScheduleDetails.getActivePeriodTimestampKey(now)

                if (_currentTimestampKey.value != timestampKeyFromTime) {
                    _currentTimestampKey.value = timestampKeyFromTime
                    Log.d("ViewModel", "Effect 0 (ViewModel): Live preview. currentTimestampKey updated to $timestampKeyFromTime. Groovy!")
                } else {
                    Log.d(
                        "ViewModel",
                        "Effect 0 (ViewModel): Live preview. currentTimestampKey is already $timestampKeyFromTime, no change needed. Still groovy.",
                    )
                }
            }
        }

        // Effect 1: Fetch currentlyDisplayedImageId based on currentTimestampKey
        viewModelScope.launch {
            currentTimestampKey.collect { key ->
                // Collect from the StateFlow
                val context = getApplication<Application>().applicationContext
                val targetKeyForRating = getFrottageTargetKey(context) // Assuming getFrottageTargetKey is accessible
                Log.d(
                    "ViewModel",
                    "Effect 1 (ViewModel): currentTimestampKey: $key, targetKey: $targetKeyForRating, supportsFrottageRatingSystem: ${SettingsManager.currentWallpaperSource.supportsFrottageRatingSystem}",
                )
                if (SettingsManager.currentWallpaperSource.supportsFrottageRatingSystem && key != null) {
                    val imageId =
                        ImageMetadataService.fetchAndParseImageId(
                            context,
                            key,
                            targetKeyForRating,
                        )
                    _currentlyDisplayedImageId.value = imageId
                    _isRatingEnabled.value = !imageId.isNullOrBlank()
                    Log.d(
                        "ViewModel",
                        "Effect 1 (ViewModel - Rating System Supported): currentlyDisplayedImageId set to $imageId, isRatingEnabled: ${_isRatingEnabled.value}",
                    )
                } else {
                    _currentlyDisplayedImageId.value = null
                    _isRatingEnabled.value = false
                    Log.d(
                        "ViewModel",
                        "Effect 1 (ViewModel - No Rating System Support/No Timestamp): Clearing imageId, disabling rating. Supports rating system: ${SettingsManager.currentWallpaperSource.supportsFrottageRatingSystem}",
                    )
                }
            }
        }

        // Effect 2: Load displayedRating based on currentlyDisplayedImageId
        viewModelScope.launch {
            currentlyDisplayedImageId.collect { imageId ->
                // Collect from the StateFlow
                val context = getApplication<Application>().applicationContext
                Log.d("ViewModel", "Effect 2 (ViewModel): currentlyDisplayedImageId: $imageId")
                if (_isRatingEnabled.value && imageId != null) { // Check internal isRatingEnabled state
                    _displayedRating.value = RatingPersistence.loadRating(context, imageId)
                    Log.d("ViewModel", "Effect 2 (ViewModel): displayedRating loaded: ${_displayedRating.value} for ID: $imageId")
                } else {
                    _displayedRating.value = 0
                    Log.d("ViewModel", "Effect 2 (ViewModel): Rating disabled or no imageId, displayedRating reset to 0.")
                }
            }
        }

        // Construct ImageRequest for Preview
        viewModelScope.launch {
            currentTimestampKey.collect { key ->
                // Collect from the StateFlow
                val context = getApplication<Application>().applicationContext
                Log.d("ViewModel", "[DEBUG] Construct ImageRequest Effect (ViewModel): currentTimestampKey changed to: $key")
                if (key != null) {
                    val wallpaperSource = SettingsManager.currentWallpaperSource
                    val schedule = wallpaperSource.schedule
                    val imageUrl = wallpaperSource.imageSetting.url.invoke(context, key)
                    Log.d("ViewModel", "[DEBUG] Construct ImageRequest Effect (ViewModel): Derived URL from active source: $imageUrl")
                    val request = schedule.imageRequest(imageUrl, context, key)
                    _imageRequestForPreview.value = request
                    Log.d(
                        "ViewModel",
                        "[DEBUG] Construct ImageRequest Effect (ViewModel): Created ImageRequest: $request for URL: $imageUrl with diskCacheKey: $key. POPULATED.",
                    )
                } else {
                    _imageRequestForPreview.value = null
                    Log.d(
                        "ViewModel",
                        "[DEBUG] Construct ImageRequest Effect (ViewModel): imageRequestForPreview is NULL (timestampKey also likely null).",
                    )
                }
            }
        }
        // Initial trigger to load data
        forceUIRefresh()
    }

    fun manuallySetCurrentWallpaper() {
        if (_isManualSetInProgress.value) {
            Log.d("ViewModel", "Manual set already in progress, ignoring new request.")
            return
        }
        val timestampKeyToSet = _currentTimestampKey.value
        if (timestampKeyToSet == null) {
            Log.w("ViewModel", "Cannot manually set wallpaper, currentTimestampKey is null.")
            _manualSetResultMessage.value = "Frottage alert: No image selected to set."
            return
        }

        viewModelScope.launch {
            Log.d(
                "ViewModel",
                "ManuallySetWallpaper: Coroutine LAUNCHED for key: $timestampKeyToSet. Current _isManualSetInProgress before this launch: ${_isManualSetInProgress.value}",
            )
            _isManualSetInProgress.value = true
            Log.d("ViewModel", "ManuallySetWallpaper: _isManualSetInProgress set to TRUE for key: $timestampKeyToSet")
            _manualSetResultMessage.value = null // Clear previous message

            try {
                WallpaperSetter.setWallpaper(getApplication(), timestampKeyToSet)
                Log.i("ViewModel", "Manual wallpaper set successful for key: $timestampKeyToSet. Groovy!")
                // Analytics: Track successful manual wallpaper set
                val context = getApplication<Application>().applicationContext
                val properties = mutableMapOf<String, Any?>()
                _currentlyDisplayedImageId.value?.let { properties["image_id"] = it }
                properties["target_name"] = getFrottageTargetKey(context)
                properties["theme"] = if (isDarkTheme(context)) "dark" else "light"
                AnalyticsService.trackEvent(
                    context = context,
                    eventName = "set_wallpaper_button",
                    eventProperties = properties,
                )
            } catch (e: Exception) {
                Log.e("ViewModel", "Manual wallpaper set failed for key: $timestampKeyToSet", e)
                _manualSetResultMessage.value = "Frottage fail: ${e.message ?: "Unknown error"}"
            } finally {
                _isManualSetInProgress.value = false
                Log.d("ViewModel", "ManuallySetWallpaper: _isManualSetInProgress set to FALSE in finally block for key: $timestampKeyToSet")
                Log.d("ViewModel", "Manual wallpaper set finished for key: $timestampKeyToSet")
            }
        }
    }

    fun clearManualSetResultMessage() {
        _manualSetResultMessage.value = null
    }

    fun handleRatingChange(
        newRating: Int,
        imageId: String?,
        context: Context,
    ) {
        if (imageId.isNullOrBlank()) {
            Log.w("ViewModel", "handleRatingChange called with null or blank imageId. Cannot process rating.")
            return
        }
        if (!_isRatingEnabled.value) { // Double check if rating is somehow triggered while disabled
            Log.w("ViewModel", "handleRatingChange called while rating is not enabled. Ignoring.")
            return
        }

        _displayedRating.value = newRating // Update UI state immediately

        viewModelScope.launch {
            Log.d("ViewModel", "Rating changed for $imageId to $newRating. Saving and submitting.")
            RatingPersistence.saveRating(context, imageId, newRating)
            submitRating(context, newRating, imageId) // Assuming submitRating is a suspend fun or handles its own context
        }
    }

    // Public method to update rating from UI, if needed for two-way binding or complex logic
    // For now, MainActivity's StarRatingBar directly updates its local state and calls persistence/network.
    // If rating state itself needed to be in ViewModel and survive config changes, we'd add a method here.
    // fun updateUserRating(rating: Int) { _displayedRating.value = rating }
}
