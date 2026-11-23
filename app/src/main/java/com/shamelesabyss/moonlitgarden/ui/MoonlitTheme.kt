package com.shamelesabyss.moonlitgarden.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val NightColors = darkColorScheme(
    primary = Color(0xFFC8A5EF),
    onPrimary = Color(0xFF231431),
    secondary = Color(0xFFDFB6E5),
    onSecondary = Color(0xFF27162F),
    tertiary = Color(0xFFE8C991),
    background = Color(0xFF130D21),
    onBackground = Color(0xFFF4EAF6),
    surface = Color(0xFF21172F),
    onSurface = Color(0xFFF4EAF6),
    surfaceVariant = Color(0xFF352543),
    onSurfaceVariant = Color(0xFFD6C5DE),
    outline = Color(0xFF6B577B),
)

@Composable
fun MoonlitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, content = content)
}

object GardenPalette {
    val Night = Color(0xFF130D21)
    val DeepViolet = Color(0xFF21172F)
    val Card = Color(0xFF2A1E39)
    val Border = Color(0xFF554066)
    val Lavender = Color(0xFFC8A5EF)
    val Rose = Color(0xFFDFB6E5)
    val MoonGold = Color(0xFFE8C991)
    val SoftText = Color(0xFFD6C5DE)
    val Leaf = Color(0xFF99C6AC)
    val Ember = Color(0xFFF7B485)
}

@Composable
fun GardenCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GardenPalette.DeepViolet),
        border = BorderStroke(1.dp, GardenPalette.Border),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            content = content,
        )
    }
}

@Composable
fun GardenAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (enabled) GardenPalette.Border else GardenPalette.Border.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GardenPalette.SoftText),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, fontWeight = FontWeight.Medium)
    }
}
