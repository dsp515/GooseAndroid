package com.goose.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Goose Design System ────────────────────────────────────────────────────
// Mirrors GooseTheme.swift with Bevel-inspired dark health aesthetic.
// Primary dark background: rgb(0.06, 0.09, 0.11) → #0F1619

object GooseColors {
    // Backgrounds
    val DeepBackground = Color(0xFF0F1619)      // GooseTheme.deviceBackground
    val CardBackground = Color(0xFF1A2228)      // Elevated card
    val CardBorder = Color(0xFF243040)          // Subtle border
    val SurfaceVariant = Color(0xFF1F2A35)

    // Text
    val TextPrimary = Color(0xFFF0F4F8)
    val TextSecondary = Color(0xFF8B9BAB)
    val TextTertiary = Color(0xFF4A5568)
    val TextMuted = Color(0xFF3D4E5C)

    // Accent / Metric colors
    val GreenMetric = Color(0xFF4ADE80)         // Good recovery / sleep
    val YellowMetric = Color(0xFFFBBF24)        // Moderate
    val OrangeMetric = Color(0xFFFB923C)        // High strain
    val RedMetric = Color(0xFFFF6B6B)           // Poor / overreached

    // Brand
    val GooseBlue = Color(0xFF3B82F6)
    val GoosePurple = Color(0xFF8B5CF6)
    val GooseCyan = Color(0xFF22D3EE)
    val GooseIndigo = Color(0xFF6366F1)

    // Recovery ring colors (WHOOP-like)
    val RecoveryGreen = Color(0xFF4ADE80)
    val RecoveryYellow = Color(0xFFF59E0B)
    val RecoveryRed = Color(0xFFEF4444)

    // Sleep stage colors (Bevel-inspired)
    val SleepDeep = Color(0xFF6366F1)
    val SleepREM = Color(0xFF8B5CF6)
    val SleepLight = Color(0xFF60A5FA)
    val SleepAwake = Color(0xFFFF6B6B)

    // Tab bar
    val TabActive = Color(0xFFF0F4F8)
    val TabInactive = Color(0xFF4A5568)

    // Gradient endpoints
    val GradientStart = Color(0xFF0F1619)
    val GradientEnd = Color(0xFF162030)
}

private val DarkColorScheme = darkColorScheme(
    primary = GooseColors.GooseBlue,
    secondary = GooseColors.GoosePurple,
    tertiary = GooseColors.GooseCyan,
    background = GooseColors.DeepBackground,
    surface = GooseColors.CardBackground,
    surfaceVariant = GooseColors.SurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = GooseColors.TextPrimary,
    onSurface = GooseColors.TextPrimary,
    onSurfaceVariant = GooseColors.TextSecondary,
    outline = GooseColors.CardBorder,
    outlineVariant = GooseColors.TextMuted,
    error = GooseColors.RedMetric
)

// Goose only uses dark mode (like the iOS app which uses .deviceBackground always)
@Composable
fun GooseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = GooseTypography,
        content = content
    )
}

// ─── Typography ──────────────────────────────────────────────────────────────

// Using system default sans-serif (Inter equivalent on Android)
val GooseTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)
