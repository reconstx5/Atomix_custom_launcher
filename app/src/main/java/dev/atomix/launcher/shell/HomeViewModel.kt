package dev.atomix.launcher.shell

import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.prefs.FavouriteApps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything Home draws. [nowMs] ticks so "min left" and progress stay current between guide refreshes. */
data class HomeUi(val live: LiveState, val apps: List<AppEntry>, val nowMs: Long)

/** Home's data: Live TV (refreshed every minute while Home is visible) and favourite apps. */
class HomeViewModel(
    private val repo: LiveRepository,
    private val favourites: FavouriteApps,
    catalog: AppCatalog,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _ui = MutableStateFlow(HomeUi(repo.state.value, favourites.visible(), now()))
    val ui: StateFlow<HomeUi> = _ui

    private var loop: Job? = null

    init {
        scope.launch { repo.state.collect { s -> _ui.value = _ui.value.copy(live = s) } }
        scope.launch { catalog.apps.collect { refreshApps() } }
    }

    fun refreshApps() { _ui.value = _ui.value.copy(apps = favourites.visible()) }

    fun refreshNow() { scope.launch { repo.refresh() } }

    /**
     * Writes the pinned channels' order to Atomix when edit mode ends; channels missing from [order] are unpinned.
     * If the server refuses, the row goes back to the server's order.
     */
    fun saveChannelOrder(order: List<String>, onError: () -> Unit = {}) {
        scope.launch {
            try {
                repo.saveOrder(order)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // LiveRepository has already put the previous order back. Say so: the row springing back, with the
                // channels just unpinned restored, is otherwise indistinguishable from a mis-press.
                onError()
            }
        }
    }

    /** Saves the apps row's order at once (local only); apps missing from [order] leave Home. */
    fun saveAppOrder(order: List<String>) {
        favourites.setVisibleOrder(order)
        refreshApps()
    }

    fun onVisible() {
        loop?.cancel()
        loop = scope.launch {
            launch {
                while (isActive) {
                    repo.refresh()
                    delay(REFRESH_MS)
                }
            }
            while (isActive) {
                _ui.value = _ui.value.copy(nowMs = now())
                delay(TICK_MS)
            }
        }
    }

    fun onHidden() {
        loop?.cancel()
        loop = null
    }

    private companion object {
        const val REFRESH_MS = 60_000L
        const val TICK_MS = 15_000L
    }
}
