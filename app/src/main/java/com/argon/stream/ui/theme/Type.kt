package com.argon.stream.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun style(weight: FontWeight, size: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp
)

/** Bold, tight headings and calm body text: a streaming-app look that stays readable from a sofa. */
val ArgonTypography = Typography(
    displaySmall = style(FontWeight.ExtraBold, 34, 40, -0.8),
    headlineLarge = style(FontWeight.ExtraBold, 30, 36, -0.7),
    headlineMedium = style(FontWeight.Bold, 26, 32, -0.5),
    headlineSmall = style(FontWeight.Bold, 22, 28, -0.3),
    titleLarge = style(FontWeight.Bold, 20, 26, -0.2),
    titleMedium = style(FontWeight.SemiBold, 16, 22),
    titleSmall = style(FontWeight.SemiBold, 14, 20),
    bodyLarge = style(FontWeight.Normal, 16, 24),
    bodyMedium = style(FontWeight.Normal, 14, 20),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.1),
    labelLarge = style(FontWeight.SemiBold, 14, 20, 0.1),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.2),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.3)
)

/** Softer, rounder corners throughout (cards, sheets, dialogs, chips). */
val ArgonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
