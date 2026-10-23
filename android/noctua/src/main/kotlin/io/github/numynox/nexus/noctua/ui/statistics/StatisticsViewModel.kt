package io.github.numynox.nexus.noctua.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.BuildConfig
import kotlinx.coroutines.Job
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

    fun load(): Job = viewModelScope.launch {
        if (_stats.value !is Loadable.Ready) _stats.value = Loadable.Loading
        _stats.value = loadable { ReadingStats.from(repository.readingStatistics(BuildConfig.STATISTICS_WEEKS)) }
    }
}
