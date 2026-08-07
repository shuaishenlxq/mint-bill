package com.xl.bill.mint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = MintPrimary,
    onPrimary = Color.White,
    primaryContainer = MintContainerLight,
    onPrimaryContainer = MintTextPrimaryLight,
    secondary = SkyBlueDeep,
    onSecondary = Color.White,
    secondaryContainer = SkyBlue,
    onSecondaryContainer = MintTextPrimaryLight,
    tertiary = ExpenseRose,
    background = MintBgLight,
    onBackground = MintTextPrimaryLight,
    surface = MintCardLight,
    onSurface = MintTextPrimaryLight,
    surfaceVariant = MintContainerLight,
    onSurfaceVariant = MintTextSecondaryLight,
    outline = MintBorderLight,
    outlineVariant = MintBorderLight,
    error = SoftRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = IncomeMint,
    onPrimary = Color(0xFF05312D),
    primaryContainer = MintContainerDark,
    onPrimaryContainer = MintTextPrimaryDark,
    secondary = SkyBlueDeep,
    onSecondary = Color(0xFF05262E),
    secondaryContainer = Color(0xFF2E4044),
    onSecondaryContainer = MintTextPrimaryDark,
    tertiary = ExpenseRose,
    background = MintBgDark,
    onBackground = MintTextPrimaryDark,
    surface = MintCardDark,
    onSurface = MintTextPrimaryDark,
    surfaceVariant = MintContainerDark,
    onSurfaceVariant = MintTextSecondaryDark,
    outline = MintBorderDark,
    outlineVariant = MintBorderDark,
    error = ExpenseRose,
    onError = Color(0xFF3A0A12)
)

@Composable
fun MintBillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MintTypography,
        shapes = MintShapes,
        content = content
    )
}
