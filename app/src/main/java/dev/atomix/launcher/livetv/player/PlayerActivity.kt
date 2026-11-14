package dev.atomix.launcher.livetv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.atomix.launcher.AppGraph
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.livetv.source.PlayCaps
import dev.atomix.launcher.livetv.source.PlayReply
import dev.atomix.launcher.ui.kit.Effects
import dev.atomix.launcher.ui.kit.LocalEffects
import dev.atomix.launcher.prefs.FocusStyle
import dev.atomix.launcher.prefs.clockIs24
import dev.atomix.launcher.ui.theme.AtomixTheme
import dev.atomix.launcher.ui.theme.LookState
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live TV inside the launcher. One player and one surface for the activity's life; channel changes swap the source.
 * Back closes overlays one at a time, then returns to where playback started with the last channel as the result.
 * Leaving the screen any other way (Home, another app) ends playback.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private lateinit var graph: AppGraph
    private lateinit var player: ExoPlayer
    private lateinit var session: PlayerSession
    private lateinit var factory: MediaFactory
    private lateinit var zap: ZapQueue
    private lateinit var numbers: NumberEntry
    private val layers = Layers()

    // Overlay state (Compose reads these).
    private var layerSet by mutableStateOf(emptySet<Layer>())
    private var shown by mutableStateOf<Channel?>(null)
    private var sessionState by mutableStateOf<SessionState>(SessionState.Idle)
    private var ready by mutableStateOf(false)
    /** When the player last ran out of pictures, or null while they are arriving. */
    private var bufferingSince: Long? = null
    private val stall = StallWatch()
    private var live by mutableStateOf(LiveInfo(paused = false, behindMs = 0))
    private var controls by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)
    private var nowMs by mutableLongStateOf(System.currentTimeMillis())
    private var source by mutableStateOf(ZapSource.Pinned)

    private var loaded: PlayReply? = null
    private var bannerJob: Job? = null
    private var noticeJob: Job? = null

    private val pinned: List<Channel> get() = when (val s = graph.live.state.value) {
        is LiveState.Ready -> s.pinned
        is LiveState.Offline -> s.pinned
        else -> emptyList()
    }.filterNot { it.radio }
    private val all: List<Channel> get() = ((graph.live.state.value as? LiveState.Ready)?.all ?: pinned).filterNot { it.radio }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = AppGraph.get(this)
        source = runCatching { ZapSource.valueOf(intent.getStringExtra(EXTRA_SOURCE).orEmpty()) }.getOrDefault(ZapSource.Pinned)
        val id = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val first = (all + pinned).firstOrNull { it.id == id }
        val client = graph.liveClient()
        if (first == null || client == null) { finish(); return }

        factory = MediaFactory(graph.authedHttp) { path -> graph.liveClient()?.url(path)?.toString() }
        session = PlayerSession(client, CAPS, graph.scope, isWidevineSupported = MediaFactory::widevineSupported, onFailure = { Log.w(TAG, "session: $it") })
        zap = ZapQueue(lifecycleScope, tune = ::startChannel)
        numbers = NumberEntry(lifecycleScope) { n -> layers.hide(Layer.NumberEntry); sync(); tuneByNumber(n) }

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { ready = true; bufferingSince = null; session.onPlayerReady() }
                // Only once pictures have started: tuning itself can take 20 s on this server, and the session has
                // its own first-picture deadline for that. `ready` is cleared on every new tune.
                if (state == Player.STATE_BUFFERING && ready && bufferingSince == null) bufferingSince = System.currentTimeMillis()
                keepScreenOn()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateLive()
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare()
                } else {
                    Log.w(TAG, "player: ${error.errorCodeName}", error)
                    session.onPlayerError()
                }
            }
        })

        lifecycleScope.launch { session.state.collect(::onSession) }
        lifecycleScope.launch {
            while (isActive) { nowMs = System.currentTimeMillis(); updateLive(); checkStall(); delay(1_000) }
        }
        lifecycleScope.launch { numbers.typed.collect { typedText = it } }

        onBackPressedDispatcher.addCallback(this) { back() }

        // The overlay wears the launcher's look; set here too, for a player started while the launcher is not alive.
        LookState.look = graph.prefs.look
        val is24 = clockIs24(LookState.look.clock, DateFormat.is24HourFormat(this))
        setContent {
            AtomixTheme {
                CompositionLocalProvider(LocalEffects provides Effects(graph.prefs.reducedEffects, LookState.look.focus == FocusStyle.RingAndGlow)) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(factory = { ctx -> SurfaceView(ctx).also(player::setVideoSurfaceView) }, modifier = Modifier.fillMaxSize())
                        shown?.let { ch ->
                            PlayerOverlay(
                                state = sessionState, shown = ch, ready = ready, layers = layerSet, controls = controls,
                                live = live, nowMs = nowMs, is24 = is24, typed = typedText, notice = notice,
                                pinned = pinned, all = all, listSource = source, loader = graph.imageLoader,
                                url = { graph.liveClient()?.url(it)?.toString() },
                                actions = OverlayActions(
                                    togglePause = ::togglePause,
                                    back30 = ::back30,
                                    jumpToLive = ::jumpToLive,
                                    openList = ::openList,
                                    pick = { c, src -> source = src; layers.hide(Layer.ChannelList); sync(); zap.cancel(); shown = c; startChannel(c); showBanner() },
                                    retry = { shown?.let(::startChannel) },
                                    nextChannel = { zapBy(+1) },
                                    home = ::finish,
                                ),
                            )
                        }
                    }
                }
            }
        }

        shown = first
        startChannel(first)
        showBanner()
    }

    private var typedText by mutableStateOf("")

    private fun onSession(s: SessionState) {
        Log.i(TAG, "state ${s::class.simpleName}")
        sessionState = s
        when (s) {
            is SessionState.Playing -> if (s.reply != loaded) {
                loaded = s.reply
                ready = false
                val media = factory.build(s.reply)
                if (media == null) session.onPlayerError()
                else { player.setMediaSource(media); player.prepare(); player.playWhenReady = true }
            }
            is SessionState.Tuning, is SessionState.Failed, SessionState.Idle -> {
                if (loaded != null) { player.stop(); player.clearMediaItems(); loaded = null }
                ready = false
            }
        }
        controls = controlsNow()
        keepScreenOn()
    }

    private fun startChannel(ch: Channel) {
        session.tune(ch)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CHANNEL_ID, ch.id))
    }

    private fun zapBy(delta: Int) {
        val from = shown ?: return
        val list = zapList(source, pinned, all)
        shown = zap.step(list, from, delta)
        showBanner()
    }

    private fun tuneByNumber(n: Int) {
        val ch = findByNumber(all, n)
        if (ch == null) { showNotice("No channel $n"); return }
        zap.cancel()
        shown = ch
        startChannel(ch)
        showBanner()
    }

    private fun showNotice(text: String) {
        noticeJob?.cancel()
        notice = text
        noticeJob = lifecycleScope.launch { delay(2_000); notice = null }
    }

    // Layers

    private fun sync() { layerSet = Layer.entries.filter(layers::isOpen).toSet() }

    private fun showBanner() {
        layers.show(Layer.Banner)
        controls = controlsNow()
        if (controls) layers.show(Layer.Controls)
        sync()
        pokeBanner()
    }

    /** The banner (and controls) hide 5 s after the last key. */
    private fun pokeBanner() {
        if (!layers.isOpen(Layer.Banner)) return
        bannerJob?.cancel()
        bannerJob = lifecycleScope.launch {
            delay(BANNER_MS)
            layers.hide(Layer.Controls); layers.hide(Layer.Banner); sync()
        }
    }

    private fun openList() {
        layers.hide(Layer.Controls); layers.hide(Layer.Banner)
        layers.show(Layer.ChannelList)
        sync()
    }

    private fun back() {
        val top = layers.top()
        if (layers.back()) {
            if (top == Layer.NumberEntry) numbers.clear()
            sync()
        } else {
            finish()
        }
    }

    // Controls

    private fun window(): Timeline.Window? {
        val t = player.currentTimeline
        if (t.isEmpty) return null
        return t.getWindow(player.currentMediaItemIndex, Timeline.Window())
    }

    private fun controlsNow(): Boolean {
        val reply = (sessionState as? SessionState.Playing)?.reply ?: return false
        val w = window()?.durationMs?.takeIf { it != C.TIME_UNSET }
        return controlsSupported(reply, w)
    }

    private fun updateLive() {
        if (!::player.isInitialized) return
        val w = window()
        val behind = if (w != null && w.isLive && w.defaultPositionMs != C.TIME_UNSET) w.defaultPositionMs - player.currentPosition else 0
        live = LiveInfo(paused = !player.playWhenReady, behindMs = behind.coerceAtLeast(0))
        val c = controlsNow()
        if (c != controls) controls = c
    }

    /**
     * A stream can stop while the session still looks healthy — the server keeps answering pings but no pictures
     * arrive. Without this the last frame would sit there with no message for as long as that lasts.
     */
    private fun checkStall() {
        when (stall.tick(bufferingSince, nowMs, paused = !player.playWhenReady)) {
            StallAction.None -> Unit
            StallAction.Tuning -> ready = false
            StallAction.Failed -> { ready = false; session.onPlayerError() }
        }
    }

    private fun togglePause() { player.playWhenReady = !player.playWhenReady; updateLive() }

    private fun back30() { player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0)); updateLive() }

    /** +30 s, but never past live. */
    private fun forward30() {
        val w = window()
        val target = player.currentPosition + SEEK_MS
        if (w == null || w.defaultPositionMs == C.TIME_UNSET || target >= w.defaultPositionMs) player.seekToDefaultPosition()
        else player.seekTo(target)
        updateLive()
    }

    private fun jumpToLive() { player.seekToDefaultPosition(); player.playWhenReady = true; updateLive() }

    // Keys (spec §5.6)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::session.isInitialized || event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        pokeBanner()
        val listOpen = layers.isOpen(Layer.ChannelList)
        val failed = sessionState is SessionState.Failed
        val controlsOpen = layers.isOpen(Layer.Controls) && controls
        when (val code = event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP ->
                if (!listOpen || code != KeyEvent.KEYCODE_DPAD_UP) { zapBy(+1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN ->
                if (!listOpen || code != KeyEvent.KEYCODE_DPAD_DOWN) { zapBy(-1); return true }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                layers.show(Layer.NumberEntry); sync(); numbers.digit(code - KeyEvent.KEYCODE_0); return true
            }
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                layers.show(Layer.NumberEntry); sync(); numbers.digit(code - KeyEvent.KEYCODE_NUMPAD_0); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (controls) {
                    when (code) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> player.playWhenReady = true
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> player.playWhenReady = false
                        else -> togglePause()
                    }
                    showBanner()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { if (controls) { back30(); showBanner() }; return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (controls) { forward30(); showBanner() }; return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (!listOpen && !failed && !controlsOpen) { showBanner(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (!listOpen && !failed && !controlsOpen) { openList(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (!listOpen && !failed && !controlsOpen) return true // ◀/▶ never seek on their own
        }
        return super.dispatchKeyEvent(event)
    }

    private fun keepScreenOn() {
        if (!::player.isInitialized) return
        val on = sessionState is SessionState.Playing && ready
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Leaving the screen (Home, another app) ends playback; Back has already set the result. */
    override fun onStop() {
        super.onStop()
        if (::session.isInitialized) { zap.cancel(); session.close() }
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) player.release()
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        private const val TAG = "AtomixPlayer"
        private const val EXTRA_SOURCE = "source"
        private const val BANNER_MS = 5_000L
        private const val SEEK_MS = 30_000L

        val CAPS = PlayCaps(
            video = listOf("h264", "hevc"),
            audio = listOf("aac", "mp3", "ac3"),
            containers = listOf("mp4", "ts"),
            hls = true,
        )

        fun intent(context: Context, channelId: String, source: ZapSource): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_CHANNEL_ID, channelId)
                .putExtra(EXTRA_SOURCE, source.name)
    }
}
