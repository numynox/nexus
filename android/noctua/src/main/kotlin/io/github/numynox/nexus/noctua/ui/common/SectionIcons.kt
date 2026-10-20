package io.github.numynox.nexus.noctua.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The Material counterpart of each lucide icon in the web's SECTION_ICONS.
 * Keys are what the database stores; unknown keys (and the emoji they
 * replaced) fall back to the newspaper.
 */
fun sectionIcon(key: String?): ImageVector = when (key) {
    "globe" -> Icons.Rounded.Public
    "landmark" -> Icons.Rounded.AccountBalance
    "cpu" -> Icons.Rounded.Memory
    "atom" -> Icons.Rounded.Science
    "rocket" -> Icons.Rounded.RocketLaunch
    "briefcase" -> Icons.Rounded.Work
    "scale" -> Icons.Rounded.Balance
    "trophy" -> Icons.Rounded.EmojiEvents
    "dumbbell" -> Icons.Rounded.FitnessCenter
    "gamepad" -> Icons.Rounded.SportsEsports
    "clapperboard" -> Icons.Rounded.Movie
    "music" -> Icons.Rounded.MusicNote
    "camera" -> Icons.Rounded.PhotoCamera
    "utensils" -> Icons.Rounded.Restaurant
    "leaf" -> Icons.Rounded.Eco
    "heart" -> Icons.Rounded.Favorite
    "graduation-cap" -> Icons.Rounded.School
    "plane" -> Icons.Rounded.Flight
    "lightbulb" -> Icons.Rounded.Lightbulb
    else -> Icons.Rounded.Newspaper
}
