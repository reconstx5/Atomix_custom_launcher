package dev.atomix.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.atomix.launcher.apps.InputEntry
import dev.atomix.launcher.apps.TvInputs
import dev.atomix.launcher.apps.liveTvIntent
import dev.atomix.launcher.apps.safeStart
import dev.atomix.launcher.atomix.ROUTE_LIVE_SETTINGS
import dev.atomix.launcher.atomix.atomixIntent
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.livetv.player.PlayerActivity
import dev.atomix.launcher.livetv.player.ZapSource
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.shell.HomeViewModel
import dev.atomix.launcher.shell.ShellScreen
import dev.atomix.launcher.ui.apps.AppsTab
import dev.atomix.launcher.ui.apps.AppsTabState
import dev.atomix.launcher.ui.home.HomeActions
import dev.atomix.launcher.ui.home.HomeScreen
import dev.atomix.launcher.ui.home.ROW_ON_NOW
import dev.atomix.launcher.ui.home.InputsDialog
import dev.atomix.launcher.ui.home.clockText
import dev.atomix.launcher.ui.kit.Effects
import dev.atomix.launcher.ui.kit.LocalEffects
import dev.atomix.launcher.ui.kit.Tab
import dev.atomix.launcher.ui.livetv.LiveTvTab
import dev.atomix.launcher.ui.livetv.LiveTvTabState
import dev.atomix.launcher.ui.livetv.ROW_CHANNELS
import androidx.lifecycle.lifecycleScope
import dev.atomix.launcher.ui.settings.SettingsActions
import dev.atomix.launcher.ui.settings.SettingsInfo
import dev.atomix.launcher.ui.settings.SettingsScreen
import dev.atomix.launcher.ui.settings.SettingsState
import dev.atomix.launcher.ui.splash.SplashPolicy
import dev.atomix.launcher.ui.splash.SplashScreen
import dev.atomix.launcher.ui.setup.SetupFlow
import dev.atomix.launcher.ui.setup.SetupViewModel
import dev.atomix.launcher.ui.theme.AtomixTheme

class SetupHolder(graph: AppGraph) : ViewModel() {
    val vm = SetupViewModel(graph::apiFor, graph.locator, graph.store, viewModelScope)
}

class HomeHolder(graph: AppGraph) : ViewModel() {
    val vm = HomeViewModel(graph.live, graph.favouriteApps, graph.apps, viewModelScope)
}

private const val TUNER_INPUT = "tv-tuner"

class HomeActivity : ComponentActivity() {
    private lateinit var graph: AppGraph
    private val home: HomeHolder by lazy {
        ViewModelProvider(this, viewModelFactory { initializer { HomeHolder(graph) } })[HomeHolder::class.java]
    }
    private val homeMemory by lazy { FocusMemory(graph.prefs, "home") }
    private val liveTabMemory by lazy { FocusMemory(graph.prefs, "liveTab") }
    private val appsTabMemory by lazy { FocusMemory(graph.prefs, "appsTab") }

    /** Where the playing channel was started from: Back from playback restores focus there. */
    private var playedFrom = Tab.Home

    /** Bumped on every return to Home; the visible screen restores its remembered focus. */
    private val restoreTick = mutableIntStateOf(0)
    private val tab = mutableStateOf(Tab.Home)
    private val signedIn = mutableStateOf(false)
    /** Settings covers the whole shell while open; Back closes it. */
    private val settingsOpen = mutableStateOf(false)
    /** Reduced effects, applied at once when Settings changes it. */
    private val reduced = mutableStateOf(false)
    /** The cold-start splash is still on screen; any key skips it. */
    private val splashing = mutableStateOf(false)
    private val splashPolicy get() = SplashPolicy.Process
    private val settings by lazy {
        SettingsState(graph::liveClient, graph.live, graph.store, { graph.locator.locate(graph.store.serverUrl, graph.store.lastKnownUrl) }, graph.prefs)
    }

    /**
     * Playback returns the last channel watched: Home focuses its card (or keeps the card that started it), the
     * Live TV tab its row.
     */
    private val player = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getStringExtra(PlayerActivity.EXTRA_CHANNEL_ID) ?: return@registerForActivityResult
        val live = home.vm.ui.value.live
        if (playedFrom == Tab.LiveTv) {
            val i = (live as? LiveState.Ready)?.all?.indexOfFirst { it.id == id } ?: -1
            if (i >= 0) liveTabMemory.remember(FocusTarget(ROW_CHANNELS, id, i))
        } else {
            val pinned = when (live) {
                is LiveState.Ready -> live.pinned
                is LiveState.Offline -> live.pinned
                else -> emptyList()
            }
            val i = pinned.indexOfFirst { it.id == id }
            if (i >= 0) homeMemory.remember(FocusTarget(ROW_ON_NOW, id, i))
        }
        restoreTick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = AppGraph.get(this)
        signedIn.value = graph.store.token != null && graph.store.profileId != null
        reduced.value = graph.prefs.reducedEffects
        splashing.value = splashPolicy.claim(recreated = savedInstanceState != null)
        val appsState = AppsTabState(graph.favouriteApps, graph.apps)
        val liveState = LiveTvTabState(graph.live, lifecycleScope) { toast("Couldn't save that pin. Try again.") }
        setContent {
            AtomixTheme {
                CompositionLocalProvider(LocalEffects provides Effects(reduced.value)) {
                    if (splashing.value) {
                        SplashScreen(reduced = reduced.value) { splashing.value = false }
                    } else if (!signedIn.value) {
                        val holder = viewModel { SetupHolder(graph) }
                        LaunchedEffect(Unit) { holder.vm.start() }
                        SetupFlow(holder.vm) { signedIn.value = true; home.vm.onVisible() }
                    } else {
                        val ui by home.vm.ui.collectAsState()
                        LaunchedEffect(ui.live) {
                            if (ui.live == LiveState.SignedOut) { graph.store.clearAuth(); signedIn.value = false }
                        }
                        var blurred by remember { mutableStateOf<ImageBitmap?>(null) }
                        var inputs by remember { mutableStateOf<List<InputEntry>?>(null) }
                        val is24 = DateFormat.is24HourFormat(this)
                        if (settingsOpen.value) {
                            SettingsScreen(
                                state = settings,
                                info = SettingsInfo(
                                    server = { graph.store.serverUrl?.toString()?.removeSuffix("/") },
                                    profile = graph.store.profileName,
                                    version = versionName(),
                                    homeOverrideOn = settings.homeOverrideOn(Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)),
                                ),
                                reduced = reduced.value,
                                nowMs = ui.nowMs,
                                actions = SettingsActions(
                                    testChannel = ::testChannel,
                                    manageSources = { launch(atomixIntent(this, ROUTE_LIVE_SETTINGS)) },
                                    switchProfile = ::switchProfile,
                                    setReduced = { settings.reducedEffects = it; reduced.value = it },
                                    close = ::closeSettings,
                                ),
                            )
                        } else ShellScreen(
                            tab = tab.value,
                            onTab = { tab.value = it; restoreTick.intValue++ },
                            clock = clockText(ui.nowMs, is24),
                            blurred = blurred,
                            onInputs = { inputs = listOf(InputEntry(TUNER_INPUT, "TV tuner")) + TvInputs(this).hdmi() },
                            onSettings = ::openSettings,
                        ) { current ->
                            when (current) {
                                Tab.Home -> HomeScreen(
                                    ui = ui,
                                    loader = graph.imageLoader,
                                    url = { graph.liveClient()?.url(it)?.toString() },
                                    is24 = is24,
                                    memory = homeMemory,
                                    restoreTick = restoreTick.intValue,
                                    actions = HomeActions(
                                        play = { playChannel(it, Tab.Home) },
                                        openApp = { launch(it.launch) },
                                        allApps = { tab.value = Tab.Apps },
                                        chooseChannels = { tab.value = Tab.LiveTv },
                                        openSettings = ::openSettings,
                                        retry = home.vm::refreshNow,
                                        unavailable = { toast("Can't reach the TV server") },
                                        saveChannels = home.vm::saveChannelOrder,
                                        saveApps = home.vm::saveAppOrder,
                                    ),
                                    onArt = { blurred = it },
                                )
                                Tab.LiveTv -> LiveTvTab(
                                    state = liveState,
                                    ui = ui,
                                    loader = graph.imageLoader,
                                    url = { graph.liveClient()?.url(it)?.toString() },
                                    is24 = is24,
                                    memory = liveTabMemory,
                                    restoreTick = restoreTick.intValue,
                                    onPlay = { playChannel(it, Tab.LiveTv) },
                                )
                                Tab.Apps -> AppsTab(
                                    state = appsState,
                                    loader = graph.imageLoader,
                                    memory = appsTabMemory,
                                    restoreTick = restoreTick.intValue,
                                    onLaunch = { launch(it.launch) },
                                    onFavouritesChanged = { home.vm.refreshApps() },
                                )
                            }
                        }
                        inputs?.let { list ->
                            InputsDialog(list, onPick = { inputs = null; openInput(it) }, onDismiss = { inputs = null })
                        }
                    }
                }
            }
        }
    }

    /** Any key skips the splash; the press itself is swallowed so it doesn't also act on Home. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (splashing.value) {
            if (event.action == KeyEvent.ACTION_UP) splashing.value = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openInput(input: InputEntry) {
        if (input.id == TUNER_INPUT) liveTvIntent(packageManager)?.let(::launch) ?: toast("The TV tuner app isn't installed")
        else launch(TvInputs(this).intentFor(input))
    }

    private fun openSettings() { settingsOpen.value = true }

    private fun closeSettings() {
        settingsOpen.value = false
        restoreTick.intValue++
    }

    /** Plays a channel from Settings; Back from the player returns to Settings. */
    private fun testChannel(id: String) {
        val pinned = (home.vm.ui.value.live as? LiveState.Ready)?.pinned.orEmpty().any { it.id == id }
        playedFrom = Tab.Home
        player.launch(PlayerActivity.intent(this, id, if (pinned) ZapSource.Pinned else ZapSource.All))
    }

    /** Keeps the sign-in but forgets the profile, so setup opens on the profile list. */
    private fun switchProfile() {
        settingsOpen.value = false
        graph.store.profileId = null
        graph.store.profileName = null
        graph.store.cachedChannelsJson = null
        graph.store.cachedFavouriteIds = null
        signedIn.value = false
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

    /** Zapping follows the list playback started from: pinned channels from Home, every channel from Live TV. */
    private fun playChannel(channel: Channel, from: Tab) {
        val source = if (from == Tab.LiveTv) ZapSource.All else ZapSource.Pinned
        playedFrom = from
        player.launch(PlayerActivity.intent(this, channel.id, source))
    }

    /** Pressing Home while on another tab returns to the Home tab. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tab.value = Tab.Home
        restoreTick.intValue++
    }

    override fun onResume() {
        super.onResume()
        if (signedIn.value) {
            home.vm.onVisible()
            restoreTick.intValue++
        }
    }

    override fun onPause() {
        super.onPause()
        home.vm.onHidden()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun launch(intent: Intent) {
        if (!safeStart(intent)) toast("Couldn't open that")
    }
}
