package io.github.numynox.nexus.noctua.ui.common

import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.noctua.data.Article
import io.github.numynox.nexus.noctua.data.NoctuaRepository
import io.github.numynox.nexus.noctua.data.Recommendation
import io.github.numynox.nexus.noctua.data.Recommender
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Recommendations as a screen shows them: what was loaded, with the reader's
 * own recommending and withdrawing over it — optimistic, and put back if the
 * write fails. Shared by the feed and the library.
 */
class Recommendations(
    private val repository: NoctuaRepository,
    private val scope: CoroutineScope,
    private val messages: MutableSharedFlow<String>,
) {
    private val _byArticle = MutableStateFlow<Map<Long, Recommendation>>(emptyMap())
    val byArticle: StateFlow<Map<Long, Recommendation>> = _byArticle.asStateFlow()

    val me: String? get() = repository.currentUserId

    fun loaded(recommendations: List<Recommendation>) {
        _byArticle.value = recommendations.associateBy { it.article.id }
    }

    /** Recommends the article to everyone, or withdraws the reader's own recommendation. */
    fun toggle(article: Article) {
        val me = me ?: return
        val before = _byArticle.value
        val current = before[article.id]
        val withdrawing = current?.mine == true
        _byArticle.value = before.toMutableMap().apply {
            if (current != null && withdrawing) {
                val rest = current.recommenders.filter { it.userId != me }
                if (rest.isEmpty()) remove(article.id) else put(article.id, current.copy(recommenders = rest, mine = false))
            } else {
                val self = Recommender(me, "you")
                put(
                    article.id,
                    current?.copy(recommenders = current.recommenders + self, mine = true)
                        ?: Recommendation(article, Instant.now(), listOf(self), mine = true, fromOthers = false),
                )
            }
        }
        messages.tryEmit(if (withdrawing) "Recommendation withdrawn" else "Recommended to everyone")
        scope.launch {
            runCatching { if (withdrawing) repository.unrecommend(article.id) else repository.recommend(article.id) }
                .onFailure {
                    // Put it back: the highlight should not claim what is not stored.
                    _byArticle.value = before
                    messages.tryEmit(it.describe())
                }
        }
    }
}
