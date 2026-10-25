package com.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

/** Keep the leading column aligned, with an explicit current-choice mark beyond text color alone. */
@Composable
fun EchoSelectionIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(24.dp).semantics {
            this.selected = selected
            if (selected) contentDescription = "当前选中"
        },
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}
