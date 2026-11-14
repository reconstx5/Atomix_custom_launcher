package dev.atomix.launcher.ui.settings

import android.content.ComponentName
import dev.atomix.launcher.data.HttpException
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.livetv.source.LiveClient
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.LiveSource
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.prefs.LauncherPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl

/** The Live TV section: [admin] profiles may refresh and manage sources; [error] is the last thing that went wrong. */
data class LiveSettingsUi(
    val sources: List<LiveSource> = emptyList(),
    val admin: Boolean = false,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

/** What Settings shows and does. [locate] runs the server locator; the result becomes the saved address. */
class SettingsState(
    private val client: () -> LiveClient?,
    private val repo: LiveRepository,
    private val store: SessionStore,
    private val locate: suspend () -> HttpUrl?,
    private val prefs: LauncherPrefs,
) {
    private val _live = MutableStateFlow(LiveSettingsUi())
    val live: StateFlow<LiveSettingsUi> = _live

    var reducedEffects: Boolean
        get() = prefs.reducedEffects
        set(v) { prefs.reducedEffects = v }

    /** Reads /api/live/status. */
    suspend fun load() {
        val api = client() ?: run { _live.value = _live.value.copy(loading = false, error = "Not connected to a TV server"); return }
        _live.value = _live.value.copy(loading = true)
        _live.value = try {
            val s = api.status()
            LiveSettingsUi(sources = s.sources, admin = s.admin, enabled = s.enabled)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _live.value.copy(loading = false, error = "Can't reach the TV server")
        }
    }

    /** Asks the server to re-read one source, then reloads the status. A refusal leaves the list as it was. */
    suspend fun refreshSource(id: String) {
        val api = client() ?: return
        try {
            api.refreshSource(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val name = _live.value.sources.firstOrNull { it.id == id }?.name ?: "that source"
            val why = if (e is HttpException && e.code == 403) "Only an admin can refresh Live TV sources." else "Couldn't refresh $name. Try again."
            _live.value = _live.value.copy(error = why)
            return
        }
        load()
    }

    /** The first pinned TV channel, else the first TV channel; null with no channels. */
    fun testChannelId(): String? = when (val s = repo.state.value) {
        is LiveState.Ready -> (s.pinned.firstOrNull() ?: s.all.firstOrNull())?.id
        is LiveState.Offline -> s.pinned.firstOrNull()?.id
        else -> null
    }

    /** Looks for the server again; when found, it becomes the saved address. */
    suspend fun findServerAgain(): Boolean {
        val found = locate() ?: return false
        if (found != store.serverUrl) {
            store.lastKnownUrl = store.serverUrl
            store.serverUrl = found
        }
        return true
    }

    /**
     * Whether the Home-button service is on, from Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES. Entries are compared
     * as components, because the list may hold the service in its short form (package/.home.HomeOverrideService),
     * which is the same service.
     */
    fun homeOverrideOn(enabledServices: String?): Boolean {
        val home = ComponentName.unflattenFromString(HOME_SERVICE)
        return enabledServices.orEmpty().split(':').any { ComponentName.unflattenFromString(it.trim()) == home }
    }

    companion object {
        const val HOME_SERVICE = "dev.atomix.launcher/dev.atomix.launcher.home.HomeOverrideService"
    }
}

/** "just now", "5 min ago", "4 h ago", "2 days ago"; null when [thenMs] is unknown. */
fun ago(thenMs: Long?, nowMs: Long): String? {
    if (thenMs == null) return null
    val min = (nowMs - thenMs).coerceAtLeast(0) / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 24 * 60 -> "${min / 60} h ago"
        min < 2 * 24 * 60 -> "1 day ago"
        else -> "${min / (24 * 60)} days ago"
    }
}
