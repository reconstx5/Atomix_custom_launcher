package dev.atomix.launcher.shell

import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.InputEntry
import dev.atomix.launcher.apps.TUNER
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.prefs.HiddenApps
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

/**
 * Everything Home draws: its rows, in order. [nowMs] ticks so "min left" and progress stay current between guide
 * refreshes. [inputs] is what the Inputs row holds: the TV's HDMI ports, then the tuner (empty until first read).
 */
data class HomeUi(val live: LiveState, val rows: List<DrawnRow>, val nowMs: Long, val inputs: List<InputEntry> = emptyList())

/** Home's data: Live TV (refreshed every minute while Home is visible) and the rows Home draws. */
class HomeViewModel(
    private val repo: LiveRepository,
    private val rows: RowStore,
    private val hidden: HiddenApps,
    private val catalog: AppCatalog,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    /** The TV's HDMI ports ([dev.atomix.launcher.apps.TvInputs.hdmi]): slow to ask, so read only as Home shows. */
    private val hdmi: () -> List<InputEntry> = { emptyList() },
) {
    private var inputs = emptyList<InputEntry>()

    private val _ui = MutableStateFlow(HomeUi(repo.state.value, drawn(), now()))
    val ui: StateFlow<HomeUi> = _ui

    private var loop: Job? = null

    init {
        scope.launch { repo.state.collect { s -> _ui.value = _ui.value.copy(live = s) } }
        scope.launch { catalog.apps.collect { refreshApps() } }
        scope.launch { rows.rows.collect { refreshApps() } }
    }

    private fun drawn(): List<DrawnRow> {
        val byPkg = catalog.apps.value.associateBy { it.packageName }
        return drawnRows(rows.rows.value, byPkg::get, hidden::isHidden, inputs = inputs.size)
    }

    /** Draws the rows again: after apps are installed or removed, rows change, or apps are hidden or shown. */
    fun refreshApps() { _ui.value = _ui.value.copy(rows = drawn()) }

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

    /** Saves an app row's order at once (local only); its drawn apps missing from [order] leave that row only. */
    fun saveAppOrder(rowId: String, order: List<String>) {
        val drawn = _ui.value.rows.firstOrNull { it.spec.id == rowId }?.apps.orEmpty().map { it.packageName }.toSet()
        rows.update { it.reordered(rowId, order) { pkg -> pkg in drawn } }
        refreshApps()
    }

    /** Reads the TV's inputs again (a port may have been renamed or added) and redraws the rows with them. */
    private fun refreshInputs() {
        inputs = runCatching(hdmi).getOrDefault(emptyList()) + TUNER
        _ui.value = _ui.value.copy(inputs = inputs, rows = drawn())
    }

    fun onVisible() {
        refreshInputs()
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
