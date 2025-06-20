package com.frottage.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PromptText(
    prompt: String?,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    val textToShow = if (prompt.isNullOrBlank()) "" else "„$prompt“"

    Text(
        text = textToShow,
        textAlign = if (isExpanded) TextAlign.Justify else TextAlign.Start,
        style =
            TextStyle(
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp,
                lineBreak = if (isExpanded) LineBreak.Paragraph else LineBreak.Simple,
                hyphens = if (isExpanded) Hyphens.Auto else Hyphens.None,
            ),
        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
        overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier =
            modifier
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                ).clickable {
                    if (textToShow.isNotEmpty()) { // Only allow expanding if there's text
                        isExpanded = !isExpanded
                    }
                },
    )
}
