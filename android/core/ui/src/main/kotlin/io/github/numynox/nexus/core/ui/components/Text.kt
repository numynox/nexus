package io.github.numynox.nexus.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.theme.tabular

/**
 * A fuel price as German stations print it: 1.78⁹ — the tenth of a cent
 * raised and small. Same as the web app's station cards.
 */
@Composable
fun PriceText(
    price: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    suffix: String = "",
) {
    val formatted = NexusFormat.number(price, 3)
    val text = remember(formatted, suffix) {
        buildAnnotatedString {
            append(formatted.dropLast(1))
            withStyle(SpanStyle(fontSize = style.fontSize * 0.6f, baselineShift = BaselineShift(0.55f))) {
                append(formatted.last())
            }
            if (suffix.isNotEmpty()) append(suffix)
        }
    }
    Text(text, modifier = modifier, style = style.tabular(), color = color)
}

/**
 * A number that springs to its new value instead of jumping — used on every
 * statistic, so loading a car feels like the numbers settle into place.
 */
@Composable
fun AnimatedNumberText(
    value: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "number",
    )
    Text(format(animated.toDouble()), modifier = modifier, style = style.tabular(), color = color)
}
