package dev.atomix.launcher.shell

import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.prefs.HiddenApps
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.RowSort
import dev.atomix.launcher.prefs.RowStore
import dev.atomix.launcher.prefs.reordered
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

/**
 * What an app row draws: its apps that are installed and not hidden from Atomix TV, in the row's order, or by name
 * (ignoring case) when the row is sorted "A to Z".
 */
fun drawnApps(row: HomeRowSpec, installed: List<AppEntry>, isHidden: (String) -> Boolean): List<AppEntry> {
    val byPkg = installed.associateBy { it.packageName }
    val apps = row.apps.filterNot(isHidden).mapNotNull { byPkg[it] }
    return if (row.sort == RowSort.AtoZ) apps.sortedBy { it.label.lowercase() } else apps
}

/** Home's data: Live TV (refreshed every minute while Home is visible) and the apps of the "Your apps" row. */
class HomeViewModel(
    private val repo: LiveRepository,
    private val rows: RowStore,
    private val hidden: HiddenApps,
    private val catalog: AppCatalog,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _ui = MutableStateFlow(HomeUi(repo.state.value, yourApps(), now()))
    val ui: StateFlow<HomeUi> = _ui

    private var loop: Job? = null

    init {
        scope.launch { repo.state.collect { s -> _ui.value = _ui.value.copy(live = s) } }
        scope.launch { catalog.apps.collect { refreshApps() } }
        scope.launch { rows.rows.collect { refreshApps() } }
    }

    private fun yourApps(): List<AppEntry> =
        rows.rows.value.firstOrNull { it.id == ROW_APPS }?.let { drawnApps(it, catalog.apps.value, hidden::isHidden) }.orEmpty()

    fun refreshApps() { _ui.value = _ui.value.copy(apps = yourApps()) }

    fun refreshNow() { scope.launch { repo.refresh() } }

    /**
     * Writes the order of the channels on Home to Atomix when editing ends. It only rearranges: nothing goes on or
     * off Home by it. If the server refuses, the row goes back to the server's order.
     */
    fun saveChannelOrder(order: List<String>, onError: () -> Unit = {}) {
        scope.launch {
            try {
                repo.saveOrder(order)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // LiveRepository has already put the previous order back. Say so: the row springing back is otherwise
                // indistinguishable from a mis-press.
                onError()
            }
        }
    }

    /** Saves the apps row's order at once (local only); apps missing from [order] leave Home. */
    fun saveAppOrder(order: List<String>) {
        val drawn = yourApps().map { it.packageName }.toSet()
        rows.update { it.reordered(ROW_APPS, order) { pkg -> pkg in drawn } }
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
