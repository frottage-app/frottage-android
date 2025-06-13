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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

object ImageSaver {
    private const val TAG = "ImageSaver"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

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
            // onSaveAttemptFinalized will be called by the launcher's result in MainActivity
            // or if the user doesn't grant permission.
            // For this path, the immediate attempt concludes by requesting permission.
            // The caller (MainActivity) should manage isLoading state until permission result.
            return // Important: return here, actual save will be re-attempted if permission granted
        }

        // Proceed if permission is granted or not needed (API Q+ for specific MediaStore operations)
        Log.d(TAG, "Permission granted or not required. Proceeding with actual save.")
        var success = false
        var message: String? = null

        try {
            val inferredFileName = imageUrl.substringAfterLast('/')
            val inferredExtension = inferredFileName.substringAfterLast('.', "jpg")
            val inferredMimeType =
                when (inferredExtension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }

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

            uri?.let { imageUri ->
                withContext(Dispatchers.IO) {
                    var inputStream: java.io.InputStream? = null
                    var outputStream: OutputStream? = null
                    try {
                        val url = URL(imageUrl)
                        val request = Request.Builder().url(url).build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                inputStream = response.body?.byteStream()
                                if (inputStream != null) {
                                    outputStream = resolver.openOutputStream(imageUri)
                                    if (outputStream != null) {
                                        inputStream.copyTo(outputStream)
                                        Log.i(TAG, "Groovy! Image bytes directly copied to MediaStore: $imageUri")
                                    } else {
                                        throw Exception("Failed to get OutputStream from ContentResolver.")
                                    }
                                } else {
                                    throw Exception("Response body was null for image download from $imageUrl")
                                }
                            } else {
                                throw Exception(
                                    "HTTP error ${response.code} fetching image from $imageUrl. Response: ${response.body?.string()}",
                                )
                            }
                        }
                    } finally {
                        inputStream?.close()
                        outputStream?.close()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                success = true
                message = "Image saved to Pictures/Frottage! Groovy frottage!"
                Log.i(TAG, "Image save process complete for URI: $imageUri. Total frottage!")
            } ?: throw Exception("MediaStore URI was null, frottage fail!")
        } catch (e: Exception) {
            success = false
            message = "Frottage fail! Could not save image: ${e.message}"
            Log.e(TAG, "Frottage error during save from URL $imageUrl: ${e.message}", e)
        } finally {
            onSaveAttemptFinalized(success, message)
        }
    }
}
