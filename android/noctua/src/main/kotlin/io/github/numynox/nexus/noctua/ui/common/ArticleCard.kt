package io.github.numynox.nexus.noctua.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import io.github.numynox.nexus.core.ui.theme.NexusThemeDefaults
import io.github.numynox.nexus.noctua.data.Article
import io.github.numynox.nexus.noctua.data.Density
import io.github.numynox.nexus.noctua.ui.feed.relativeAge
import kotlinx.coroutines.launch

/** What can be done to an article from its card. */
data class ArticleActions(
    val open: (Article) -> Unit,
    val toggleStar: (Article) -> Unit,
    val toggleRead: (Article) -> Unit,
    val share: (Article) -> Unit,
)

/**
 * An article in a list. Tap opens it; swipe right stars, swipe left marks it
 * read or unread; a long press offers the same and sharing. Read and seen
 * articles fade back, unless [faded] says otherwise (the library lists).
 */
@Composable
fun ArticleCard(
    article: Article,
    read: Boolean,
    starred: Boolean,
    seen: Boolean,
    density: Density,
    actions: ArticleActions,
    modifier: Modifier = Modifier,
    faded: Boolean? = null,
    caption: String? = null,
    similar: List<Article> = emptyList(),
    similarExpanded: Boolean = false,
    onToggleSimilar: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    val alpha by animateFloatAsState(if (faded ?: (read || seen)) 0.62f else 1f, label = "faded")

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { SwipeBackground(state.dismissDirection, starred, read) },
        onDismiss = { direction ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> actions.toggleStar(article)
                SwipeToDismissBoxValue.EndToStart -> actions.toggleRead(article)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Nothing is removed: the card springs back with its new state.
            scope.launch { state.reset() }
        },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                ArticleBody(article, read, starred, density, actions, caption, Modifier.alpha(alpha))
                if (similar.isNotEmpty()) {
                    SimilarStories(similar, similarExpanded, onToggleSimilar, actions, Modifier.alpha(alpha))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArticleBody(
    article: Article,
    read: Boolean,
    starred: Boolean,
    density: Density,
    actions: ArticleActions,
    caption: String?,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { actions.open(article) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(horizontal = 16.dp, vertical = if (density == Density.Compact) 10.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FeedIcon(article.feedIcon, Modifier.size(16.dp))
                    Text(
                        listOfNotNull(article.feedName, relativeAge(article.time).ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (starred) Icon(Icons.Rounded.Star, contentDescription = "Starred", tint = NexusThemeDefaults.extendedColors.warning, modifier = Modifier.size(16.dp))
                    if (read) Icon(Icons.Rounded.CheckCircle, contentDescription = "Read", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Text(
                    article.title,
                    style = if (density == Density.Compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    maxLines = if (density == Density.Compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (density == Density.Comfortable && article.summary != null) {
                    Text(
                        article.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (density == Density.Comfortable && article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(84.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Open") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                onClick = { menu = false; actions.open(article) },
            )
            DropdownMenuItem(
                text = { Text(if (starred) "Remove star" else "Star") },
                leadingIcon = { Icon(if (starred) Icons.Rounded.StarOutline else Icons.Rounded.Star, contentDescription = null) },
                onClick = { menu = false; actions.toggleStar(article) },
            )
            DropdownMenuItem(
                text = { Text(if (read) "Mark unread" else "Mark read") },
                leadingIcon = { Icon(if (read) Icons.Rounded.MarkEmailUnread else Icons.Rounded.CheckCircle, contentDescription = null) },
                onClick = { menu = false; actions.toggleRead(article) },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                onClick = { menu = false; actions.share(article) },
            )
        }
    }
}

/** "+2 similar stories", and the other feeds' versions of it when opened. */
@Composable
private fun SimilarStories(
    similar: List<Article>,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: ArticleActions,
    modifier: Modifier,
) {
    Column(modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    "${similar.size} more on this story",
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 6.dp)) {
                similar.forEach { other ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .combinedClickable(onClick = { actions.open(other) })
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FeedIcon(other.feedIcon, Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(other.feedName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(other.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** A feed's favicon, or the RSS mark when it has none or it does not load. */
@Composable
fun FeedIcon(url: String?, modifier: Modifier = Modifier) {
    val fallback: @Composable () -> Unit = {
        Icon(Icons.Rounded.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize())
    }
    if (url == null) {
        Box(modifier) { fallback() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier.clip(MaterialTheme.shapes.extraSmall),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, starred: Boolean, read: Boolean) {
    val (color, icon, label) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Triple(
            NexusThemeDefaults.extendedColors.warningContainer,
            if (starred) Icons.Rounded.StarOutline else Icons.Rounded.Star,
            if (starred) "Unstar" else "Star",
        )
        SwipeToDismissBoxValue.EndToStart -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            if (read) Icons.Rounded.MarkEmailUnread else Icons.Rounded.CheckCircle,
            if (read) "Unread" else "Read",
        )
        SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, null, "")
    }
    val background by animateColorAsState(color, label = "swipe")
    val content = if (direction == SwipeToDismissBoxValue.StartToEnd) NexusThemeDefaults.extendedColors.onWarningContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(background)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (direction == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}
