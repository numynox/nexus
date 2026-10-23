package io.github.numynox.nexus.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A pull to refresh: whether it is running, and how to start one. */
class PullRefresh(val refreshing: Boolean, val onRefresh: () -> Unit)

/**
 * A pull to refresh that shows until [reload]'s job is done.
 *
 * Not until the data changes: a screen that waited for its state to move on
 * spun forever when the refresh brought back exactly what was there, since an
 * equal value is not emitted again. The job's end is the one signal every
 * reload gives.
 */
@Composable
fun rememberPullRefresh(reload: () -> Job): PullRefresh {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentReload by rememberUpdatedState(reload)
    return PullRefresh(refreshing) {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                try {
                    currentReload().join()
                } finally {
                    refreshing = false
                }
            }
        }
    }
}
