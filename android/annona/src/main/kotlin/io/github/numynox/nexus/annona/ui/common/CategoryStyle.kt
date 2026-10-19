package io.github.numynox.nexus.annona.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KebabDining
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Nature
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** A category colour key and its swatch — the web's CATEGORY_COLORS (lib/categoryMeta.ts), same hues. */
data class CategoryColor(val key: String, val label: String, val color: Color)

val CategoryColors = listOf(
    CategoryColor("rose", "Rose", Color.hsl(350f, 0.60f, 0.55f)),
    CategoryColor("coral", "Coral", Color.hsl(15f, 0.70f, 0.55f)),
    CategoryColor("amber", "Amber", Color.hsl(38f, 0.80f, 0.50f)),
    CategoryColor("yellow", "Yellow", Color.hsl(50f, 0.85f, 0.48f)),
    CategoryColor("lime", "Lime", Color.hsl(80f, 0.55f, 0.45f)),
    CategoryColor("green", "Green", Color.hsl(140f, 0.50f, 0.45f)),
    CategoryColor("teal", "Teal", Color.hsl(175f, 0.55f, 0.42f)),
    CategoryColor("sky", "Sky", Color.hsl(200f, 0.65f, 0.52f)),
    CategoryColor("blue", "Blue", Color.hsl(220f, 0.60f, 0.55f)),
    CategoryColor("violet", "Violet", Color.hsl(265f, 0.55f, 0.55f)),
    CategoryColor("pink", "Pink", Color.hsl(320f, 0.60f, 0.57f)),
    CategoryColor("brown", "Brown", Color.hsl(25f, 0.40f, 0.45f)),
    CategoryColor("slate", "Slate", Color.hsl(215f, 0.20f, 0.50f)),
)

fun categoryColor(key: String?): Color? = CategoryColors.firstOrNull { it.key == key }?.color

/** The web's icon keys (lucide names, as stored), each with its nearest Material icon. */
data class CategoryIcon(val key: String, val label: String, val icon: ImageVector)

val CategoryIcons = listOf(
    CategoryIcon("package", "Default", Icons.Rounded.Inventory2),
    CategoryIcon("shopping-basket", "Basket", Icons.Rounded.ShoppingBasket),
    CategoryIcon("apple", "Apple", Icons.Rounded.Nature),
    CategoryIcon("beef", "Meat", Icons.Rounded.KebabDining),
    CategoryIcon("candy", "Candy", Icons.Rounded.Cake),
    CategoryIcon("carrot", "Carrot", Icons.Rounded.Yard),
    CategoryIcon("cherry", "Cherry", Icons.Rounded.Spa),
    CategoryIcon("coffee", "Coffee", Icons.Rounded.Coffee),
    CategoryIcon("cookie", "Bakery", Icons.Rounded.BakeryDining),
    CategoryIcon("egg", "Egg", Icons.Rounded.Egg),
    CategoryIcon("fish", "Seafood", Icons.Rounded.SetMeal),
    CategoryIcon("flame", "Spices", Icons.Rounded.LocalFireDepartment),
    CategoryIcon("grape", "Grape", Icons.Rounded.BubbleChart),
    CategoryIcon("ice-cream-cone", "Frozen", Icons.Rounded.Icecream),
    CategoryIcon("leaf", "Herbs", Icons.Rounded.Eco),
    CategoryIcon("milk", "Dairy", Icons.Rounded.LocalDrink),
    CategoryIcon("nut", "Nuts", Icons.Rounded.Grass),
    CategoryIcon("pizza", "Pizza", Icons.Rounded.LocalPizza),
    CategoryIcon("sandwich", "Sandwich", Icons.Rounded.LunchDining),
    CategoryIcon("snowflake", "Frozen", Icons.Rounded.AcUnit),
    CategoryIcon("soup", "Soup", Icons.Rounded.RamenDining),
    CategoryIcon("wheat", "Grains", Icons.Rounded.Grain),
    CategoryIcon("wine", "Wine", Icons.Rounded.WineBar),
)

fun categoryIcon(key: String?): ImageVector = CategoryIcons.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Inventory2

