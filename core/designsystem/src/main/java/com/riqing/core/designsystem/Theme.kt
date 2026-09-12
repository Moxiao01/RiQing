package com.riqing.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Indigo = Color(0xFF3F51B5)
val IndigoLight = Color(0xFF757DE8)
val IndigoDark = Color(0xFF002984)

val ColorC1 = Color(0xFF3F51B5)
val ColorC2 = Color(0xFF009688)
val ColorC3 = Color(0xFFFF7043)
val ColorC4 = Color(0xFF7E57C2)
val ColorC5 = Color(0xFF26A69A)
val ColorC6 = Color(0xFFEC407A)
val ColorC7 = Color(0xFFFFA726)
val ColorC8 = Color(0xFF42A5F5)

/** 待办完成打勾的语义绿，不随主题 primary（靛蓝）变化。 */
val TodoDoneGreen = Color(0xFF4CAF50)

fun courseColor(token: String): Color = when (token.lowercase()) {
    "c1" -> ColorC1
    "c2" -> ColorC2
    "c3" -> ColorC3
    "c4" -> ColorC4
    "c5" -> ColorC5
    "c6" -> ColorC6
    "c7" -> ColorC7
    "c8" -> ColorC8
    else -> ColorC1
}

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAF6),
    onPrimaryContainer = IndigoDark,
    secondary = IndigoLight,
    secondaryContainer = Color(0xFFC5CAE9),
    background = Color(0xFFF7F8FC),
    surface = Color.White,
    onBackground = Color(0xFF1A1B1F),
    onSurface = Color(0xFF1A1B1F),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1A237E),
    primaryContainer = IndigoDark,
    onPrimaryContainer = Color(0xFFE8EAF6),
    secondary = Color(0xFF9FA8DA),
    background = Color(0xFF121316),
    surface = Color(0xFF1C1D22),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
)

@Composable
fun RiQingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
