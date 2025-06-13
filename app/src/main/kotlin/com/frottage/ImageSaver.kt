package com.frottage

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URL

object ImageSaver {
    private const val TAG = "ImageSaver"

    suspend fun saveImageRequiringPermissions(
        context: Context,
        imageUrl: String,
        imageUniqueId: String?, // Primarily used for naming the file if available
        onRequestPermission: () -> Unit,
        onSaveAttemptFinalized: (success: Boolean, message: String?) -> Unit,
    ) {
        Log.d(TAG, "Attempting to save image from URL: $imageUrl. Groovy!")

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Storage permission needed for pre-Q device. Requesting via callback.")
            onRequestPermission()
            return // Actual save will be re-attempted if permission granted
        }

        Log.d(TAG, "Permission granted or not required. Proceeding with actual save.")
        var finalSuccess = false
        var finalMessage: String? = null

        try {
            val inferredFileName = imageUrl.substringAfterLast('/')
            // Assuming JPG, adjust if an actual inference mechanism or parameter is added
            val inferredExtension = "jpg"
            val inferredMimeType = "image/jpeg"

            val displayName = "frottage_${imageUniqueId ?: System.currentTimeMillis()}.$inferredExtension"
            Log.d(TAG, "Using displayName: $displayName, mimeType: $inferredMimeType")

            val contentValues =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, inferredMimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Frottage")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    } else {
                        @Suppress("DEPRECATION")
                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val frottageDir = java.io.File(picturesDir, "Frottage")
                        if (!frottageDir.exists() && !frottageDir.mkdirs()) {
                            Log.w(TAG, "Frottage alert: Could not create Frottage directory at ${frottageDir.absolutePath}")
                            // Fallback or throw, depending on desired strictness. Here we let MediaStore try.
                        }
                        val imageFile = java.io.File(frottageDir, displayName)
                        put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                    }
                }

            val resolver = context.contentResolver
            val imageInsertUri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("MediaStore URI was null after insert, frottage fail!")

            withContext(Dispatchers.IO) {
                val url = URL(imageUrl)
                val request = Request.Builder().url(url).build()

                ApiClient.okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody =
                            try {
                                response.body?.string()
                            } catch (e: Exception) {
                                "Could not read error body."
                            }
                        throw Exception(
                            "HTTP error ${response.code} fetching image from $imageUrl. Response: $errorBody. What a frottage moment!",
                        )
                    }

                    val responseBody =
                        response.body
                            ?: throw Exception("Response body was null for image download from $imageUrl. This is not groovy!")

                    responseBody.byteStream().use { inputStream ->
                        resolver.openOutputStream(imageInsertUri).use { outputStream ->
                            if (outputStream == null) {
                                throw Exception("Failed to get OutputStream from ContentResolver for $imageInsertUri. Sad frottage noises.")
                            }
                            inputStream.copyTo(outputStream)
                            Log.i(TAG, "Groovy! Image bytes directly copied to MediaStore: $imageInsertUri")
                        } // outputStream auto-closed
                    } // inputStream auto-closed
                } // response auto-closed
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageInsertUri, contentValues, null, null)
            }
            finalSuccess = true
            finalMessage = "Image saved to Pictures/Frottage! Groovy frottage!"
            Log.i(TAG, "Image save process complete for URI: $imageInsertUri. Total frottage!")
        } catch (e: Exception) {
            finalSuccess = false
            finalMessage = "Frottage fail! Could not save image: ${e.message}"
            Log.e(TAG, "Frottage error during save from URL $imageUrl: ${e.message}", e)
        } finally {
            onSaveAttemptFinalized(finalSuccess, finalMessage)
        }
    }
}
