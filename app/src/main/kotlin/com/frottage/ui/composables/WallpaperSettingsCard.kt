package com.frottage.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frottage.SettingsManager

@Composable
fun WallpaperSettingsCard(modifier: Modifier = Modifier) {
    var wallpaperSettingsExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.width(300.dp),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    top = 10.dp,
                    bottom = 10.dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                wallpaperSettingsExpanded =
                                    !wallpaperSettingsExpanded
                            },
                        ).padding(
                            start = 22.dp,
                            end = 10.dp,
                            top = 8.dp,
                            bottom =
                                if (wallpaperSettingsExpanded) {
                                    0.dp
                                } else {
                                    8.dp
                                },
                        ),
            ) {
                Text(
                    text = "Wallpaper Settings",
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                    modifier =
                        Modifier.weight(
                            1f,
                        ), // Make text take available space
                )
                Icon(
                    imageVector =
                        if (wallpaperSettingsExpanded) {
                            Icons.Filled.ExpandLess
                        } else {
                            Icons.Filled.ExpandMore
                        },
                    contentDescription =
                        if (wallpaperSettingsExpanded) {
                            "Collapse"
                        } else {
                            "Expand"
                        },
                )
            }
            AnimatedVisibility(visible = wallpaperSettingsExpanded) {
                Column(
                    modifier =
                        Modifier.padding(
                            start = 10.dp,
                            end = 20.dp,
                            top = 10.dp,
                        ),
                ) {
                    LockScreenEnableCheckbox()
                    HomeScreenEnableCheckbox()
                    HomeScreenBlurCheckbox()
                }
            }
        }
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
