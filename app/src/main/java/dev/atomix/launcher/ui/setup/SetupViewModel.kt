package dev.atomix.launcher.ui.setup

import dev.atomix.launcher.data.AtomixClient
import dev.atomix.launcher.data.ATOMIX_PORT
import dev.atomix.launcher.data.HttpException
import dev.atomix.launcher.data.PollResult
import dev.atomix.launcher.data.Profile
import dev.atomix.launcher.data.ServerLocator
import dev.atomix.launcher.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

sealed interface SetupState {
    data object FindingServer : SetupState
    data class EnterAddress(val error: String?) : SetupState
    data class QuickConnect(val code: String, val secondsLeft: Int, val server: String) : SetupState
    data class ChooseProfile(val profiles: List<Profile>) : SetupState
    data class EnterPin(val profile: Profile, val error: String?) : SetupState
    data object Done : SetupState
}

/** First run: find Atomix, sign in with Quick Connect, pick who's watching. */
class SetupViewModel(
    private val apiFor: (HttpUrl) -> AtomixClient,
    private val locator: ServerLocator,
    private val store: SessionStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<SetupState>(SetupState.FindingServer)
    val state: StateFlow<SetupState> = _state
    private var job: Job? = null

    private fun api(): AtomixClient = apiFor(store.serverUrl!!)

    /** Starts from wherever the saved session leaves off. */
    fun start() = launch {
        _state.value = SetupState.FindingServer
        val url = locator.locate(store.serverUrl, store.lastKnownUrl)
        if (url == null) { _state.value = SetupState.EnterAddress(null); return@launch }
        connected(url)
    }

    /** "192.168.8.65", "192.168.8.65:8787" or a full http URL. */
    fun submitAddress(text: String) = launch {
        val url = parseAddress(text)
        if (url == null || locator.locate(url, null) != url) {
            _state.value = SetupState.EnterAddress("Atomix didn't answer at that address.")
            return@launch
        }
        connected(url)
    }

    fun choose(profile: Profile) = launch {
        if (profile.hasPin) _state.value = SetupState.EnterPin(profile, null) else select(profile, null)
    }

    fun submitPin(pin: String) = launch {
        val p = (state.value as? SetupState.EnterPin)?.profile ?: return@launch
        select(p, pin)
    }

    fun backToProfiles() = launch { loadProfiles() }

    private suspend fun connected(url: HttpUrl) {
        store.serverUrl = url
        store.lastKnownUrl = url
        if (store.token == null) quickConnect() else loadProfiles()
    }

    private suspend fun quickConnect() {
        while (true) {
            val start = api().quickConnectStart()
            val deadline = now() + start.ttlMs
            var restart = false
            while (!restart) {
                _state.value = SetupState.QuickConnect(start.code, ((deadline - now()) / 1000).toInt().coerceAtLeast(0), store.serverUrl.toString())
                delay(2_000)
                when (val r = runCatching { api().quickConnectPoll(start.poll) }.getOrElse { if (it is IOException) PollResult.Pending else throw it }) {
                    is PollResult.Approved -> { store.token = r.token; loadProfiles(); return }
                    PollResult.Expired -> restart = true
                    PollResult.Pending -> if (now() >= deadline) restart = true
                }
            }
        }
    }

    private suspend fun loadProfiles() {
        val profiles = api().profiles()
        val only = profiles.singleOrNull()
        if (only != null && !only.hasPin) select(only, null) else _state.value = SetupState.ChooseProfile(profiles)
    }

    private suspend fun select(profile: Profile, pin: String?) {
        try {
            api().selectProfile(profile.id, pin)
        } catch (e: HttpException) {
            if (e.code == 400 || e.code == 403) { _state.value = SetupState.EnterPin(profile, "Wrong PIN"); return }
            throw e
        }
        store.profileId = profile.id
        store.profileName = profile.name
        _state.value = SetupState.Done
    }

    private fun launch(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SetupState.EnterAddress("Couldn't reach Atomix. Check the computer is on, then try again.")
            }
        }
    }
}

fun parseAddress(text: String): HttpUrl? {
    val t = text.trim().trimEnd('/')
    if (t.isEmpty()) return null
    val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    val port = if (Regex(""":\d+$""").containsMatchIn(t.substringAfter("://"))) url.port else ATOMIX_PORT
    return url.newBuilder().port(port).encodedPath("/").build()
}
