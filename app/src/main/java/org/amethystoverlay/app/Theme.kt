package org.amethystoverlay.app

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF0B0F1A)
val CardBg = Color(0xFF161D2E)
val CardBgAlt = Color(0xFF1E2740)
val Accent = Color(0xFF7C4DFF)
val AccentAlt = Color(0xFF00E5FF)
val Pink = Color(0xFFFF4D8D)
val Green = Color(0xFF2ECC71)
val Orange = Color(0xFFFF9F43)
val Muted = Color(0xFFB0BAC9)
val ErrorRed = Color(0xFFFF6B6B)

val BgBrush: Brush = Brush.verticalGradient(
    listOf(Color(0xFF1A0B3D), Color(0xFF0B0F1A), Color(0xFF062A33))
)
val PlayBrush: Brush = Brush.horizontalGradient(
    listOf(Color(0xFF00C853), Color(0xFF64DD17))
)
val HeaderBrush: Brush = Brush.horizontalGradient(
    listOf(Accent, Pink, Orange)
)
