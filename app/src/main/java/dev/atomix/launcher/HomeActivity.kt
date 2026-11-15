package dev.atomix.launcher

import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.atomix.launcher.apps.InputEntry
import dev.atomix.launcher.apps.TvFacts
import dev.atomix.launcher.apps.TvInputs
import dev.atomix.launcher.apps.liveTvIntent
import dev.atomix.launcher.apps.openTvPage
import dev.atomix.launcher.apps.openTvSettings
import dev.atomix.launcher.apps.readTvFacts
import dev.atomix.launcher.apps.safeStart
import dev.atomix.launcher.atomix.ROUTE_LIVE_SETTINGS
import dev.atomix.launcher.atomix.atomixIntent
import dev.atomix.launcher.atomix.stopAtomixProcess
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.prefs.FocusStyle
import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.clockIs24
import dev.atomix.launcher.prefs.topBarShowsClock
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.livetv.player.PlayerActivity
import dev.atomix.launcher.livetv.player.ZapSource
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.shell.HomeViewModel
import dev.atomix.launcher.shell.Place
import dev.atomix.launcher.shell.placeAfterPick
import dev.atomix.launcher.shell.ShellScreen
import dev.atomix.launcher.ui.apps.AppsTab
import dev.atomix.launcher.ui.apps.AppsTabState
import dev.atomix.launcher.ui.apps.ManageAppsScreen
import dev.atomix.launcher.ui.home.HomeActions
import dev.atomix.launcher.ui.home.HomeScreen
import dev.atomix.launcher.ui.home.ROW_ON_NOW
import dev.atomix.launcher.ui.home.clockText
import dev.atomix.launcher.ui.inputs.InputsScreen
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
import dev.atomix.launcher.ui.settings.SettingsSection
import dev.atomix.launcher.ui.settings.SettingsState
import dev.atomix.launcher.ui.splash.SplashPolicy
import dev.atomix.launcher.ui.splash.SplashScreen
import dev.atomix.launcher.ui.setup.SetupFlow
import dev.atomix.launcher.ui.setup.SetupViewModel
import dev.atomix.launcher.ui.theme.AtomixTheme
import dev.atomix.launcher.ui.theme.LookState

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
    /** Home is what a new window opens on, and the window's own first focus must not replace the remembered item. */
    private val homeMemory by lazy { FocusMemory(graph.prefs, "home", untilRestored = true) }
    private val liveTabMemory by lazy { FocusMemory(graph.prefs, "liveTab") }
    private val appsTabMemory by lazy { FocusMemory(graph.prefs, "appsTab") }

    /** Where the playing channel was started from: Back from playback restores focus there. */
    private var playedFrom: Tab? = Tab.Home

    /** Bumped on every return to Home; the visible screen restores its remembered focus. */
    private val restoreTick = mutableIntStateOf(0)
    private val place = mutableStateOf(Place.Home)
    /** The Settings section to open on, when Settings was opened about one (a button under a message on Home). */
    private val settingsSection = mutableStateOf<SettingsSection?>(null)
    private val signedIn = mutableStateOf(false)
    /** Whether the Home-button service is switched on, re-read whenever the launcher comes back to the front. */
    private val homeOverrideOn = mutableStateOf(false)
    /** The HDMI ports the Inputs page lists, read each time it opens. */
    private val hdmiInputs = mutableStateOf<List<InputEntry>>(emptyList())
    /** The TV's own settings pages that can be opened, with its network and free space; read when Settings opens. */
    private val tvFacts = mutableStateOf(TvFacts(emptyList(), "", null))
    /** How Home is laid out, applied at once when Settings changes it. */
    private val homeLayout = mutableStateOf(HomeLayout())
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
        // null = opened from Settings' "Test a channel": that is not a card the user chose, so it must not become
        // the one Home returns to.
        if (playedFrom == null) {
            restoreTick.intValue++
            return@registerForActivityResult
        }
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
        LookState.look = graph.prefs.look
        homeLayout.value = graph.prefs.homeLayout
        splashing.value = splashPolicy.claim(recreated = savedInstanceState != null)
        val appsState = AppsTabState(graph.favouriteApps, graph.apps)
        val liveState = LiveTvTabState(graph.live, lifecycleScope) { toast("Couldn't change Show on Home. Try again.") }
        setContent {
            AtomixTheme {
                val look = LookState.look
                CompositionLocalProvider(LocalEffects provides Effects(reduced.value, look.focus == FocusStyle.RingAndGlow)) {
                    if (splashing.value) {
                        SplashScreen(reduced = reduced.value) { splashing.value = false }
                    } else if (!signedIn.value) {
                        val holder = viewModel { SetupHolder(graph) }
                        LaunchedEffect(Unit) { holder.vm.start() }
                        SetupFlow(holder.vm) { signedIn.value = true; home.vm.onVisible() }
                    } else {
                        val ui by home.vm.ui.collectAsState()
                        LaunchedEffect(ui.live) {
                            if (ui.live == LiveState.SignedOut) {
                                graph.store.clearAuth(); signedIn.value = false; stopAtomixProcess(this@HomeActivity)
                            }
                        }
                        // Every time the launcher shows follows the Clock setting, or the TV's own until one is chosen.
                        val tvIs24 = DateFormat.is24HourFormat(this)
                        val is24 = clockIs24(look.clock, tvIs24)
                        ShellScreen(
                            place = place.value,
                            onPlace = { place.value = it; restoreTick.intValue++ },
                            clock = if (topBarShowsClock(look.clock)) clockText(ui.nowMs, is24) else null,
                            onInputs = ::openInputs,
                            onSettings = { openSettings() },
                        ) { current ->
                            when (current) {
                                Place.Home -> HomeScreen(
                                    ui = ui,
                                    loader = graph.imageLoader,
                                    url = { graph.liveClient()?.url(it)?.toString() },
                                    is24 = is24,
                                    layout = homeLayout.value,
                                    memory = homeMemory,
                                    restoreTick = restoreTick.intValue,
                                    actions = HomeActions(
                                        play = { playChannel(it, Tab.Home) },
                                        openApp = { launch(it.launch) },
                                        allApps = { place.value = Place.Apps },
                                        chooseChannels = { place.value = Place.LiveTv },
                                        openSettings = { openSettings(it) },
                                        retry = home.vm::refreshNow,
                                        saveChannels = { order -> home.vm.saveChannelOrder(order) { toast("Couldn't save the channel order. Try again.") } },
                                        saveApps = home.vm::saveAppOrder,
                                    ),
                                )
                                Place.LiveTv -> LiveTvTab(
                                    state = liveState,
                                    ui = ui,
                                    loader = graph.imageLoader,
                                    url = { graph.liveClient()?.url(it)?.toString() },
                                    is24 = is24,
                                    memory = liveTabMemory,
                                    restoreTick = restoreTick.intValue,
                                    onPlay = { playChannel(it, Tab.LiveTv) },
                                )
                                Place.Apps -> AppsTab(
                                    state = appsState,
                                    loader = graph.imageLoader,
                                    memory = appsTabMemory,
                                    restoreTick = restoreTick.intValue,
                                    onLaunch = { launch(it.launch) },
                                    onEdit = { place.value = Place.ManageApps },
                                )
                                Place.ManageApps -> ManageAppsScreen(
                                    state = appsState,
                                    loader = graph.imageLoader,
                                    onInfo = { app ->
                                        // A fresh task, so Back from the TV's page comes straight back here and not to
                                        // whatever the TV's settings were last left showing.
                                        launch(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                        )
                                    },
                                    onChanged = { home.vm.refreshApps() },
                                )
                                Place.Inputs -> InputsScreen(
                                    hdmi = hdmiInputs.value,
                                    tuner = InputEntry(TUNER_INPUT, "TV tuner"),
                                    onPick = { place.value = placeAfterPick(openInput(it)) },
                                )
                                Place.Settings -> SettingsScreen(
                                    state = settings,
                                    info = SettingsInfo(
                                        server = { graph.store.serverUrl?.toString()?.removeSuffix("/") },
                                        profile = graph.store.profileName,
                                        version = versionName(),
                                        homeOverrideOn = homeOverrideOn.value,
                                    ),
                                    reduced = reduced.value,
                                    look = look,
                                    layout = homeLayout.value,
                                    tvIs24 = tvIs24,
                                    nowMs = ui.nowMs,
                                    tv = tvFacts.value,
                                    start = settingsSection.value,
                                    actions = SettingsActions(
                                        testChannel = ::testChannel,
                                        manageSources = { launch(atomixIntent(this, ROUTE_LIVE_SETTINGS)) },
                                        switchProfile = ::switchProfile,
                                        setReduced = { settings.reducedEffects = it; reduced.value = it },
                                        openAccessibility = { if (!openTvSettings()) toast("Couldn't open that") },
                                        openTvPage = { if (!openTvPage(it)) toast("Couldn't open that") },
                                        setLook = { graph.prefs.look = it; LookState.look = it },
                                        setLayout = { graph.prefs.homeLayout = it; homeLayout.value = it },
                                    ),
                                )
                            }
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

    /** Opens the Inputs page with the TV's HDMI ports as they are listed now. */
    private fun openInputs() {
        hdmiInputs.value = TvInputs(this).hdmi()
        place.value = Place.Inputs
    }

    /** Opens an input; false when it could not be opened (a message has said why). */
    private fun openInput(input: InputEntry): Boolean {
        if (input.id != TUNER_INPUT) return launch(TvInputs(this).intentFor(input))
        val tuner = liveTvIntent(packageManager)
        if (tuner == null) {
            toast("The TV tuner app isn't installed")
            return false
        }
        return launch(tuner)
    }

    /** Opens Settings, on [section] when it was opened about one. */
    private fun openSettings(section: SettingsSection? = null) {
        settingsSection.value = section
        tvFacts.value = readTvFacts()
        place.value = Place.Settings
    }

    /** Plays a channel from Settings; Back from the player returns to Settings. */
    private fun testChannel(id: String) {
        val pinned = (home.vm.ui.value.live as? LiveState.Ready)?.pinned.orEmpty().any { it.id == id }
        playedFrom = null
        player.launch(PlayerActivity.intent(this, id, if (pinned) ZapSource.Pinned else ZapSource.All))
    }

    /** Keeps the sign-in but forgets the profile, so setup opens on the profile list. */
    private fun switchProfile() {
        place.value = Place.Home
        graph.store.profileId = null
        graph.store.profileName = null
        graph.store.cachedChannelsJson = null
        graph.store.cachedFavouriteIds = null
        signedIn.value = false
        stopAtomixProcess(this)
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
        place.value = Place.Home
        restoreTick.intValue++
    }

    override fun onResume() {
        super.onResume()
        homeOverrideOn.value = settings.homeOverrideOn(Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        // Back from one of the TV's pages: the network or the free space may have changed there.
        if (place.value == Place.Settings) tvFacts.value = readTvFacts()
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

    /** Starts [intent]; false, with a message, when it could not be started. */
    private fun launch(intent: Intent): Boolean {
        val started = safeStart(intent)
        if (!started) toast("Couldn't open that")
        return started
    }
}
