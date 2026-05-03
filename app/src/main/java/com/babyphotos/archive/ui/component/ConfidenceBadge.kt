package com.babyphotos.archive.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ConfidenceBadge(
    confidence: Int,
    modifier: Modifier = Modifier
) {
    val (color, label) = when {
        confidence >= 80 -> Color(0xFF4CAF50) to "高"
        confidence >= 50 -> Color(0xFFFFC107) to "中"
        else -> Color(0xFFF44336) to "低"
    }

    Text(
        text = "$confidence% $label",
        modifier = modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall
    )
}
