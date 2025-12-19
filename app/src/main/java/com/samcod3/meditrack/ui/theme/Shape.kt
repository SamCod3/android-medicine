package com.samcod3.meditrack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Custom shapes with subtle rounding - less aggressive than Material 3 defaults.
 * Material 3 default: ExtraSmall=4dp, Small=8dp, Medium=12dp, Large=16dp, ExtraLarge=28dp
 * These are more refined: less rounding for a cleaner, modern look.
 */
val MediTrackShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
)
