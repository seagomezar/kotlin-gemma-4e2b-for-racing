package com.example.kotlin_chatbot

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val RacingRed = Color(0xFFFF6A00)
val RacingCyan = Color(0xFF58F5FF)
val AsphaltBlack = Color(0xFF080B10)
val CarbonGray = Color(0xFF202018)
val TrackWhite = Color(0xFFF4F7FB)
val CheckeredGray = Color(0xFFA4A9B4)

val RacingColorScheme = darkColorScheme(
    primary = RacingRed,
    onPrimary = AsphaltBlack,
    secondary = RacingCyan,
    onSecondary = AsphaltBlack,
    surface = AsphaltBlack,
    onSurface = TrackWhite,
    background = AsphaltBlack,
    onBackground = TrackWhite,
    surfaceVariant = CarbonGray,
    onSurfaceVariant = CheckeredGray,
    outline = RacingRed,
    tertiary = RacingCyan,
    onTertiary = AsphaltBlack
)

val RacingShapes = Shapes(
    small = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
    medium = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
    large = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp)
)
