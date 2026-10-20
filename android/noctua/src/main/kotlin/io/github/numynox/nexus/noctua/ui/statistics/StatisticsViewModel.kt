package io.github.numynox.nexus.noctua.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.BuildConfig
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatisticsViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository

    private val _stats = MutableStateFlow<Loadable<ReadingStats>>(Loadable.Loading)
    val stats: StateFlow<Loadable<ReadingStats>> = _stats.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_stats.value !is Loadable.Ready) _stats.value = Loadable.Loading
            _stats.value = loadable {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val reads = repository.readRecords()
                val weeks = ReadingStats.windowWeeks(reads.minOfOrNull { it.readAt }, BuildConfig.STATISTICS_WEEKS, today, zone)
                val since = ReadingStats.windowStart(weeks, today).atStartOfDay(zone).toInstant()
                coroutineScope {
                    val total = async { repository.articleCount() }
                    val published = async { repository.publishedSince(since) }
                    ReadingStats.compute(reads, published.await(), total.await(), weeks, today, zone)
                }
            }
        }
    }
}
