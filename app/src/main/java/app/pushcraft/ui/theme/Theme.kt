package app.pushcraft.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

private val Light = lightColorScheme(
    primary = Color(0xFF5B5BF6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4FF), onPrimaryContainer = Color(0xFF1B1B6E),
    secondary = Color(0xFF7C6CF2), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FF), onSecondaryContainer = Color(0xFF2A1F6B),
    tertiary = Color(0xFFDB2777),
    background = Color(0xFFF5F6FA), onBackground = Color(0xFF0E1116),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0E1116),
    surfaceVariant = Color(0xFFECEDF3), onSurfaceVariant = Color(0xFF5B6070),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFF1F2F7), surfaceContainerHigh = Color(0xFFECEDF3),
    surfaceContainerHighest = Color(0xFFE6E7EE),
    outline = Color(0xFFCDD0DA), outlineVariant = Color(0xFFE3E5EC),
    error = Color(0xFFE5484D), onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8B8BFF), onPrimary = Color(0xFF14143D),
    primaryContainer = Color(0xFF2B2B66), onPrimaryContainer = Color(0xFFE4E4FF),
    secondary = Color(0xFFB4A9FF), onSecondary = Color(0xFF1E1650),
    secondaryContainer = Color(0xFF2E2760), onSecondaryContainer = Color(0xFFE6E0FF),
    tertiary = Color(0xFFF472B6),
    background = Color(0xFF0B0C10), onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF16181D), onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF22252C), onSurfaceVariant = Color(0xFFA3A8B8),
    surfaceContainerLowest = Color(0xFF0E1014), surfaceContainerLow = Color(0xFF121419),
    surfaceContainer = Color(0xFF1A1C22), surfaceContainerHigh = Color(0xFF20232A),
    surfaceContainerHighest = Color(0xFF262A32),
    outline = Color(0xFF3A3E48), outlineVariant = Color(0xFF2A2D35),
    error = Color(0xFFFF6369), onError = Color(0xFF3B0A0C),
)

@Immutable
data class GlassColors(val fill: Color, val fillStrong: Color, val stroke: Color, val dark: Boolean)

val LocalGlass = staticCompositionLocalOf {
    GlassColors(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.85f), Color(0x140E1116), false)
}

val Mono = FontFamily.Monospace

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun PushcraftTheme(dark: Boolean, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val glass = if (dark) {
        GlassColors(Color(0xFF1A1D24).copy(alpha = 0.72f), Color(0xFF1C1F26).copy(alpha = 0.9f), Color.White.copy(alpha = 0.08f), true)
    } else {
        GlassColors(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.88f), Color(0x140E1116), false)
    }
    val context = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
