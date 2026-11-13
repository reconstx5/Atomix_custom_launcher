package dev.atomix.launcher.ui.livetv

import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.LiveState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the Live TV tab needs: every TV channel, and "Show on Home", which is Atomix's favourites under another name
 * (the code still calls it pinning). A change shows at once; if the server refuses, it reverts and [onError] is told.
 */
class LiveTvTabState(private val repo: LiveRepository, private val scope: CoroutineScope, private val onError: () -> Unit = {}) {
    private val ready get() = repo.state.value as? LiveState.Ready

    fun rows(): List<Channel> = ready?.all.orEmpty()
    fun isPinned(id: String): Boolean = ready?.pinned?.any { it.id == id } == true

    /**
     * The channel the "Show on Home" switch acts on: the focused row's, or none once that channel has left the
     * server's list, so the switch never acts on an id that is no longer there.
     */
    fun switchTarget(focusedId: String?): Channel? = focusedId?.let { id -> rows().firstOrNull { it.id == id } }

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
