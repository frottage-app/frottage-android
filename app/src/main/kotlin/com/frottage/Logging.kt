package com.frottage

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.frottage.ui.composables.WorkInfoListScreen

fun logToFile(
    context: Context,
    message: String,
) {
    val logFile = File(context.filesDir, "schedule_logs.txt")

    try {
        FileWriter(logFile, true).use { writer ->
            writer.appendLine("${formatTimestampAsLocalTime(System.currentTimeMillis())}: $message")
        }
    } catch (ioe: IOException) {
        // Handle exception, possibly logging to system log or notifying user
    }
}

fun formatTimestampAsLocalTime(timestamp: Long): String {
    val instant = Instant.ofEpochMilli(timestamp)
    val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return localDateTime.format(formatter)
}

@Composable
fun LogFileView(onClick: () -> Unit) {
    val context = LocalContext.current
    var logLines by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(context) {
        logLines = loadLogFile(context)
    }
    Column(
        // modifier =
        //     Modifier
        //         .fillMaxSize()
        //         .safeDrawingPadding(),
    ) {
        Text("Schedule:")
        WorkInfoListScreen()

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .clickable {
                        onClick()
                    },
        ) {
            items(logLines) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

suspend fun loadLogFile(context: Context): List<String> =
    withContext(Dispatchers.IO) {
        val logFile = File(context.filesDir, "schedule_logs.txt")
        if (logFile.exists()) {
            logFile.readLines()
        } else {
            listOf("Log file does not exist.")
        }
    }
