package dev.atomix.launcher.ui.home

import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.data.AtomixClient
import dev.atomix.launcher.data.AtomixJson
import dev.atomix.launcher.data.HomeResponse
import dev.atomix.launcher.data.Item
import dev.atomix.launcher.data.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val api: () -> AtomixClient?,
    private val store: SessionStore,
    private val dock: () -> List<AppEntry>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    private val focusRaw = MutableStateFlow<Item?>(null)
    /** The card the hero follows; settles 300 ms after focus stops moving. */
    val focusedItem: StateFlow<Item?> = focusRaw.debounce(300).stateIn(scope, SharingStarted.Eagerly, null)

    private var job: Job? = null

    fun onCardFocused(item: Item?) { focusRaw.value = item }

    fun refreshDock() { _state.value = _state.value.copy(dock = dock()) }

    /** Shows the cache at once, then fetches in the background. */
    fun refresh() {
        job?.cancel()
        val cached = store.cachedHomeJson?.let { runCatching { AtomixJson.parseHome(it) }.getOrNull() }
        publish(reduce(cached, null))
        job = scope.launch {
            val client = api()
            val fresh: Result<HomeResponse> = if (client == null) {
                Result.failure(java.io.IOException("No server"))
            } else {
                try {
                    val text = client.homeJson()
                    val parsed = AtomixJson.parseHome(text)
                    store.cachedHomeJson = text
                    Result.success(parsed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            val result = reduce(cached, fresh)
            if (result.first == HomeState.Status.SignedOut) store.clearAuth()
            publish(result)
        }
    }

    private fun publish(r: Pair<HomeState.Status, HomeResponse?>) {
        _state.value = HomeState(
            status = r.first,
            hero = pickHero(r.second),
            row = r.second?.let { pickRow(it.rows) },
            dock = dock(),
            profileName = store.profileName,
            libraryId = r.second?.libraries?.firstOrNull()?.id,
        )
    }
}
