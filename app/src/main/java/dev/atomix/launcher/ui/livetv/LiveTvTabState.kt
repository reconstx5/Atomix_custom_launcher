package dev.atomix.launcher.ui.livetv

import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.LiveState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the Live TV tab needs: every TV channel, and pinning shared with Atomix's favourites. A pin shows at once;
 * if the server refuses it reverts and [onError] is told.
 */
class LiveTvTabState(private val repo: LiveRepository, private val scope: CoroutineScope, private val onError: () -> Unit = {}) {
    private val ready get() = repo.state.value as? LiveState.Ready

    fun rows(): List<Channel> = ready?.all.orEmpty()
    fun isPinned(id: String): Boolean = ready?.pinned?.any { it.id == id } == true

    fun togglePin(id: String) {
        val pinned = isPinned(id)
        scope.launch {
            try {
                if (pinned) repo.unpin(id) else repo.pin(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError()
            }
        }
    }
}
