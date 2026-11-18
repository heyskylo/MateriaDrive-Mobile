package bar.materia.drive

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Materia's palette: the Material You "vibrant" scheme from the lavender seed, same as the web apps.
private val Light = lightColorScheme(
    primary = Color(0xFF8500F0), onPrimary = Color.White,
    primaryContainer = Color(0xFFEEDBFF), onPrimaryContainer = Color(0xFF2C0051),
    secondaryContainer = Color(0xFFF7D8FF), onSecondaryContainer = Color(0xFF2B1233),
    tertiaryContainer = Color(0xFFFFD6F7), onTertiaryContainer = Color(0xFF37072F),
    background = Color(0xFFFFF7FF), surface = Color(0xFFFFF7FF), onSurface = Color(0xFF1F1724),
    surfaceContainerLow = Color(0xFFFBF0FD), surfaceContainer = Color(0xFFF6EAF8), surfaceContainerHigh = Color(0xFFF0E4F2),
    surfaceContainerHighest = Color(0xFFEADFED), onSurfaceVariant = Color(0xFF4D4452),
    outline = Color(0xFF7F7483), outlineVariant = Color(0xFFD1C2D4),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFDAB9FF), onPrimary = Color(0xFF3E0076),
    primaryContainer = Color(0xFF6500B9), onPrimaryContainer = Color(0xFFF0DBFF),
    secondaryContainer = Color(0xFF553F5D), onSecondaryContainer = Color(0xFFF5D9FF),
    tertiaryContainer = Color(0xFF61385E), onTertiaryContainer = Color(0xFFFFD7F7),
    background = Color(0xFF16111B), surface = Color(0xFF16111B), onSurface = Color(0xFFEBDEF0),
    surfaceContainerLow = Color(0xFF1F1724), surfaceContainer = Color(0xFF241B2A), surfaceContainerHigh = Color(0xFF2F2535),
    surfaceContainerHighest = Color(0xFF3A3140), onSurfaceVariant = Color(0xFFCFC2D3),
    outline = Color(0xFF988E9C), outlineVariant = Color(0xFF4C4451),
)

object Brand {
    val Lavender = Color(0xFFC58CFA)
    val Ink = Color(0xFF1B0F26)
    val Lime = Color(0xFFD4F36A)
}

@Composable
fun MateriaTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
