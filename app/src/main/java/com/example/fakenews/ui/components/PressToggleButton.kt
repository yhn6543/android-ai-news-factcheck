package com.example.fakenews.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.ui.theme.toComposeColor

@Composable
fun PressToggleButton(
    press: NewsPress,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = press.buttonColor.toComposeColor()
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) selectedColor else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) selectedColor else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text(
            text = press.displayName,
            modifier = Modifier.padding(horizontal = 2.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
