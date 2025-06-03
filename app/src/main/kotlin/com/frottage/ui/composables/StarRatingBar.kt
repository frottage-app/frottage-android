package com.frottage.ui.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StarRatingBar(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxStars: Int = 5,
) {
    Row(modifier = modifier) {
        for (starIndex in 1..maxStars) {
            IconButton(onClick = { onRatingChange(starIndex) }) {
                Icon(
                    imageVector = if (starIndex <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (starIndex <= rating) "Filled Star $starIndex" else "Empty Star $starIndex",
                    tint =
                        if (starIndex <= rating) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.5f,
                            )
                        },
                )
            }
        }
    }
}
