package com.frottage

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.frottage.ui.composables.WorkInfoListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
fun LogFileView(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var ratingStats by remember { mutableStateOf<RatingPersistence.RatingStats?>(null) }

    LaunchedEffect(context) {
        logLines = loadLogFile(context)
        ratingStats = RatingPersistence.getRatingStats(context)
    }
    Column(
        modifier = modifier,
        // modifier =
        //     Modifier
        //         .fillMaxSize()
        //         .safeDrawingPadding(),
    ) {
        Text(
            "Frottage HQ Diagnostics!",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        ratingStats?.let { stats ->
            Text(
                "Overall Image Rating Grooviness:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            Text(
                "Rated Images: ${stats.count}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 20.dp),
            )
            Text(
                "Average Frottage Score: %.2f stars".format(stats.averageRating),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
        } ?: Text(
            "No frottage ratings tallied yet! Be the first to rate!",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )

        Text(
            "Schedule Log:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp),
        )
        WorkInfoListScreen()

        SelectionContainer {
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
