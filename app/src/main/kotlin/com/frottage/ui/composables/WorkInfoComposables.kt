package com.frottage.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow

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
fun WorkInfoList(
    workInfos: List<WorkInfo>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(workInfos) { workInfo ->
            WorkInfoItem(workInfo)
        }
    }
}

// Composable to display a single WorkInfo item
@Composable
fun WorkInfoItem(
    workInfo: WorkInfo,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(text = "ID: ${workInfo.id}")
        Text(text = "State: ${workInfo.state}")
        Text(text = "Tags: ${workInfo.tags.joinToString()}")
    }
}
