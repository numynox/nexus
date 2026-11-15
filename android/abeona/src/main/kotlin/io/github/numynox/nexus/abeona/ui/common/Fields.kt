package io.github.numynox.nexus.abeona.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.brandSoft
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.well

// Form pieces for sheets and settings (abeona-design.md §6, Sheet): compact
// controls in the well, one line each, so a form reads as rows rather than
// stacked outlined boxes.

/**
 * A few choices as one compact pill: the well, the chosen one in brand-soft
 * with brand text. For two to four short options (Depart | Arrive, 1st | 2nd,
 * a year); more go in [ChoicePills] or a menu.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    /** What the group chooses, for a screen reader ("Class"). */
    description: String? = null,
) {
    Surface(
        color = well.container,
        contentColor = well.content,
        shape = CircleShape,
        modifier = modifier.then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Row(Modifier.padding(2.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { option ->
                val chosen = option == selected
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (chosen) brandSoft.container else well.container)
                        .selectable(chosen, role = Role.RadioButton, onClick = { onSelect(option) })
                        .height(32.dp)
                        .defaultMinSize(minWidth = 44.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = AbeonaType.meta.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (chosen) brand else well.content,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** More choices than fit a [Segmented]: small pills that wrap, the chosen one in brand-soft. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoicePills(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val chosen = option == selected
            Surface(
                color = if (chosen) brandSoft.container else well.container,
                shape = CircleShape,
                modifier = Modifier.clip(CircleShape).selectable(chosen, role = Role.RadioButton, onClick = { onSelect(option) }),
            ) {
                Box(Modifier.height(32.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label(option),
                        style = AbeonaType.meta.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (chosen) brand else well.content,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A text field in the well: no outline, no floating label, as tall as its
 * text. The [placeholder] says what goes in; [description] names the field
 * for a screen reader when the placeholder is gone.
 */
@Composable
fun WellField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    suffix: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    textAlign: TextAlign = TextAlign.Start,
    style: TextStyle = AbeonaType.body,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = style.copy(color = ink, textAlign = textAlign),
        cursorBrush = SolidColor(brand),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        decorationBox = { field ->
            Surface(color = well.container, shape = RoundedCornerShape(12.dp)) {
                Row(
                    Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (icon != null) Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f), contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = style.copy(textAlign = textAlign), color = muted, maxLines = maxLines)
                        }
                        field()
                    }
                    if (suffix != null) Text(suffix, style = style, color = muted)
                }
            }
        },
    )
}

/**
 * One line of a sheet or of settings: the name (with its [icon] and an
 * optional [supporting] line), its control at the right. [info] folds the
 * explanation behind a small icon that opens it in place; [toggle] makes the
 * whole line a switch, [onClick] makes it a link.
 */
@Composable
fun FieldRow(
    name: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    info: String? = null,
    toggle: Boolean? = null,
    onToggle: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    nameColor: Color = Color.Unspecified,
    padding: Dp = 4.dp,
    control: @Composable RowScope.() -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val ink = if (!enabled) muted else if (nameColor != Color.Unspecified) nameColor else MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    when {
                        toggle != null -> Modifier.toggleable(toggle, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
                        onClick != null -> Modifier.clickable(enabled = enabled, onClick = onClick)
                        else -> Modifier
                    },
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = padding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (nameColor != Color.Unspecified) nameColor else muted, modifier = Modifier.padding(end = 4.dp).size(20.dp))
            }
            // The name and its info icon share the room; the control keeps the right edge.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(name, style = AbeonaType.body, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (supporting != null) Text(supporting, style = AbeonaType.meta, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (info != null) {
                    IconButton(onClick = { open = !open }, modifier = Modifier.padding(start = 4.dp).size(32.dp)) {
                        Icon(Icons.Outlined.Info, contentDescription = "About $name", tint = muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            control()
        }
        if (info != null) {
            AnimatedVisibility(open) {
                Text(
                    info,
                    style = AbeonaType.meta,
                    color = muted,
                    modifier = Modifier.padding(start = padding + if (icon != null) 32.dp else 0.dp, end = padding, bottom = 8.dp),
                )
            }
        }
    }
}
