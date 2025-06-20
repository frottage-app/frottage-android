package com.frottage

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageMetadataService {
    private const val TAG = "ImageMetadataService"

    data class ImageDetails(
        val imageId: String?,
        val purePrompt: String?,
    )

    suspend fun fetchImageDetails(
        context: Context,
        timestampKey: String,
        targetKey: String,
    ): ImageDetails? {
        val metadataUrlString = FrottageApiService.getImagesMetadataUrl(timestampKey)
        Log.d(
            TAG,
            "Fetching image details from: $metadataUrlString for target: $targetKey using Retrofit. Groovy!",
        )

        return withContext(Dispatchers.IO) {
            val metadataMap =
                try {
                    ApiClient.frottageApiService.getImageMetadata(metadataUrlString)
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Frottage disaster! Exception fetching or parsing image metadata from $metadataUrlString with Retrofit: ${e.message}",
                        e,
                    )
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                "Frottage hiccup: Could not load image details.",
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                    return@withContext null
                }

            if (!metadataMap.containsKey(targetKey)) {
                Log.w(
                    TAG,
                    "Frottage alert: TargetKey '$targetKey' not found in parsed JSON response from $metadataUrlString",
                )
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            "Frottage hiccup: Could not load image details.",
                            Toast.LENGTH_LONG,
                        ).show()
                }
                return@withContext null
            }

            val imageMetadataValue = metadataMap[targetKey]
            if (imageMetadataValue == null) {
                Log.w(
                    TAG,
                    "Frottage alert: Value for targetKey '$targetKey' was null in parsed metadata from $metadataUrlString",
                )
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            "Frottage hiccup: Could not load image details.",
                            Toast.LENGTH_LONG,
                        ).show()
                }
                return@withContext null
            }

            val imageId = imageMetadataValue.image_id.toString()
            val purePrompt = imageMetadataValue.pure_prompt
            Log.i(
                TAG,
                "Successfully fetched and parsed image_id: '$imageId' and pure_prompt: '$purePrompt' for target: '$targetKey' using Retrofit. Total frottage!",
            )
            return@withContext ImageDetails(imageId, purePrompt)
        }
    }
}
