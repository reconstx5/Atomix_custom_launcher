package dev.atomix.launcher.livetv.source

import dev.atomix.launcher.data.AuthExpiredException
import dev.atomix.launcher.data.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Live TV as Home sees it. Lists hold TV channels only (radio stays in Atomix). */
sealed interface LiveState {
    data object Loading : LiveState
    data object NotConfigured : LiveState
    data object SignedOut : LiveState
    /** The server can't be reached: last-known pins, shown dimmed and not playable. */
    data class Offline(val pinned: List<Channel>) : LiveState
    /** [pinned] in the profile's favourite order; [all] by channel number, then name. */
    data class Ready(val pinned: List<Channel>, val all: List<Channel>) : LiveState
}

/**
 * Channels and pins from Atomix, shared with Atomix's own favourites. Keeps the last good list for offline display,
 * and asks [relocate] to look for the server again once per run of failures (its address may have changed).
 */
class LiveRepository(
    private val client: () -> LiveClient?,
    private val store: SessionStore,
    private val relocate: suspend () -> Unit,
) {
    private val _state = MutableStateFlow<LiveState>(LiveState.Loading)
    val state: StateFlow<LiveState> = _state

    private var channels: List<Channel> = emptyList()
    private var favouriteIds: List<String> = emptyList()
    private var failing = false

    suspend fun refresh() {
        val api = client()
        if (api == null) { goOffline(); return }
        try {
            if (!api.status().enabled) { _state.value = LiveState.NotConfigured; failing = false; return }
            val json = api.channelsJson()
            val favs = api.favouriteIds()
            channels = parseChannels(json)
            favouriteIds = favs
            store.cachedChannelsJson = json
            store.cachedFavouriteIds = favs
            failing = false
            publish()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthExpiredException) {
            _state.value = LiveState.SignedOut
        } catch (e: Exception) {
            goOffline()
            if (!failing) { failing = true; runCatching { relocate() } }
        }
    }

    private fun goOffline() {
        val cached = store.cachedChannelsJson?.let { runCatching { parseChannels(it) }.getOrNull() }.orEmpty()
        val favs = store.cachedFavouriteIds.orEmpty()
        _state.value = LiveState.Offline(pinnedOf(cached, favs))
    }

    private fun pinnedOf(list: List<Channel>, favs: List<String>): List<Channel> {
        val tv = list.filter { !it.radio }.associateBy { it.id }
        return favs.mapNotNull { tv[it] }
    }

    private fun publish() {
        val all = channels.filter { !it.radio }
            .sortedWith(compareBy<Channel>({ it.number == null }, { it.number }, { it.name.lowercase() }))
        _state.value = LiveState.Ready(pinnedOf(channels, favouriteIds), all)
    }

    fun channel(id: String): Channel? = channels.firstOrNull { it.id == id }

    private fun isTv(id: String) = channels.any { it.id == id && !it.radio }

    suspend fun pin(id: String) = change(favouriteIds + id) { it.addFavourite(id) }

    suspend fun unpin(id: String) = change(favouriteIds - id) { it.removeFavourite(id) }

    /**
     * Saves the order of the TV channels on Home. It only rearranges: the list sent is the favourites as they are
     * now, put in [tvIds]' order. A TV favourite [tvIds] does not name (put on Home elsewhere since the row was read)
     * keeps its place after those it does; an id that is no longer a favourite (taken off Home elsewhere) is not put
     * back. The server drops favourites missing from the list, so every non-TV favourite (radio, or channels the
     * launcher doesn't show) goes after the TV ones in its existing order.
     */
    suspend fun saveOrder(tvIds: List<String>) {
        val order = if (_state.value is LiveState.Ready) reordered(tvIds, favouriteIds, ::isTv)
        // With no channel list loaded nothing counts as TV and the favourites are not known, so there is nothing to
        // merge with. distinct(): a favourite would otherwise appear in both halves and be sent twice.
        else (tvIds + favouriteIds.filter { !isTv(it) }).distinct()
        change(order) { it.setFavouriteOrder(order) }
    }

    /** Applies [next] at once and reverts if the server refuses. */
    private suspend fun change(next: List<String>, call: suspend (LiveClient) -> Unit) {
        val api = client() ?: return
        val before = favouriteIds
        favouriteIds = next.distinct()
        publishIfReady()
        try {
            call(api)
            store.cachedFavouriteIds = favouriteIds
        } catch (e: Exception) {
            favouriteIds = before
            publishIfReady()
            throw e
        }
    }

    /**
     * Only Ready is republished from the channels held in memory: an offline or signed-out screen must not turn back
     * into a playable one because a pin changed.
     */
    private fun publishIfReady() {
        if (_state.value is LiveState.Ready) publish()
    }
}

/**
 * [favourites] with its TV channels put in the order [named] gives: the named ones that are still favourites first,
 * then TV favourites [named] leaves out, then everything that is not TV, each group otherwise in its existing order.
 * Nothing is added and nothing is dropped.
 */
internal fun reordered(named: List<String>, favourites: List<String>, isTv: (String) -> Boolean): List<String> {
    val tv = favourites.filter(isTv)
    val kept = named.distinct().filter { it in tv }
    return kept + tv.filter { it !in kept } + favourites.filter { !isTv(it) }
}
