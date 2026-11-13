package dev.atomix.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import coil.ImageLoader
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.livetv.source.onAir
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.shell.HomeUi
import dev.atomix.launcher.ui.kit.EmptyState
import dev.atomix.launcher.ui.kit.LocalEffects
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import kotlinx.coroutines.delay

const val ROW_ON_NOW = "onNow"
const val ROW_APPS = "apps"

/** What Home asks its host to do. */
class HomeActions(
    val play: (Channel) -> Unit,
    val openApp: (AppEntry) -> Unit,
    val allApps: () -> Unit,
    val chooseChannels: () -> Unit,
    val openSettings: () -> Unit,
    val retry: () -> Unit,
    val unavailable: () -> Unit,
)

/**
 * Home: the focused channel's spotlight over its artwork, the "On now" row of pinned channels, and the favourite
 * apps row (peeking at the bottom until focus moves down, when the column slides up). Remembers focus and restores
 * it whenever [restoreTick] changes (returning from an app, from playback, or switching back to this tab).
 */
@Composable
fun HomeScreen(
    ui: HomeUi,
    loader: ImageLoader,
    url: (String?) -> String?,
    is24: Boolean,
    memory: FocusMemory,
    restoreTick: Int,
    actions: HomeActions,
    onArt: (ImageBitmap?) -> Unit,
) {
    val live = ui.live
    val pinned = when (live) {
        is LiveState.Ready -> live.pinned
        is LiveState.Offline -> live.pinned
        else -> emptyList()
    }
    val offline = live is LiveState.Offline
    val channelFocus = remember { HashMap<String, FocusRequester>() }
    val appFocus = remember { HashMap<String, FocusRequester>() }
    val allAppsFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    val channelList = rememberLazyListState()
    val appList = rememberLazyListState()
    var inApps by remember { mutableStateOf(memory.current()?.row == ROW_APPS) }

    // The spotlight follows card focus once it settles (300 ms).
    var focusedId by remember { mutableStateOf(memory.current()?.takeIf { it.row == ROW_ON_NOW }?.key) }
    var spotlightId by remember { mutableStateOf(focusedId) }
    LaunchedEffect(focusedId) { delay(300); spotlightId = focusedId }
    val spot = pinned.firstOrNull { it.id == spotlightId } ?: pinned.firstOrNull()
    val spotOn = spot?.let { onAir(it, ui.nowMs) }
    val art = rememberSpotlightArt(if (offline) null else url(spotOn?.programme?.image), loader)
    LaunchedEffect(art) { onArt(art?.blurred) }

    // Restore focus on every tick, and once more when the rows first fill in.
    val hasItems = pinned.isNotEmpty() || ui.apps.isNotEmpty()
    LaunchedEffect(restoreTick, hasItems, live::class) {
        val rows = linkedMapOf(ROW_ON_NOW to pinned.map { it.id }, ROW_APPS to ui.apps.map { it.packageName })
        // First visit with nothing pinned: start on the empty state's button, not down in the apps.
        val target = if (memory.current() == null && pinned.isEmpty()) null else memory.resolve(rows)
        withFrameNanos { }
        val ok = when (target?.row) {
            ROW_ON_NOW -> focusIn(channelList, target, channelFocus)
            ROW_APPS -> focusIn(appList, target, appFocus)
            else -> false
        }
        if (!ok) runCatching { emptyFocus.requestFocus() }.recoverCatching { allAppsFocus.requestFocus() }
    }

    val density = LocalDensity.current
    val slide = with(density) { px(390).toPx() }
    val reduced = LocalEffects.current.reduced
    val offset by animateFloatAsState(if (inApps) -slide else 0f, tween(if (reduced) 0 else 300), label = "slide")

    Box(Modifier.fillMaxSize()) {
        SpotlightBackdrop(art, dim = inApps)
        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer { translationY = offset }) {
            Box(Modifier.fillMaxWidth().height(px(616)).padding(start = px(116), end = px(116), bottom = px(8)), contentAlignment = Alignment.BottomStart) {
                Spotlight(spot, spotOn, url(spot?.logo), loader, is24)
            }
            Row(Modifier.padding(start = px(116), top = px(24)), verticalAlignment = Alignment.CenterVertically) {
                BasicText("On now", style = AtomixType.rowHeader())
            }
            Box(Modifier.height(px(318)).fillMaxWidth().onFocusChanged { if (it.hasFocus) inApps = false }) {
                val emptyPad = Modifier.padding(start = px(116), top = px(24))
                when {
                    live is LiveState.NotConfigured -> EmptyState(
                        "Live TV isn't set up on your TV server",
                        "Your apps still work. Set up a Live TV source in Settings.",
                        listOf("Open Settings" to actions.openSettings),
                        emptyPad,
                        first = Modifier.focusRequester(emptyFocus),
                    )
                    offline && pinned.isEmpty() -> EmptyState(
                        "Can't reach the TV server",
                        "Your apps still work. Live TV comes back when the server does.",
                        listOf("Try again" to actions.retry, "Settings" to actions.openSettings),
                        emptyPad,
                        first = Modifier.focusRequester(emptyFocus),
                    )
                    live is LiveState.Ready && pinned.isEmpty() -> EmptyState(
                        "Pin your channels",
                        "Choose the channels you watch most and they'll be here, ready to play.",
                        listOf("Choose channels" to actions.chooseChannels),
                        emptyPad,
                        first = Modifier.focusRequester(emptyFocus),
                    )
                    else -> OnNowRow(
                        channels = pinned,
                        nowMs = ui.nowMs,
                        dimmed = offline,
                        loader = loader,
                        logoUrl = { url(it.logo) },
                        is24 = is24,
                        listState = channelList,
                        requesters = { id -> channelFocus.getOrPut(id) { FocusRequester() } },
                        onPlay = { if (offline) actions.unavailable() else actions.play(it) },
                        onFocused = { ch, i -> focusedId = ch.id; inApps = false; memory.remember(FocusTarget(ROW_ON_NOW, ch.id, i)) },
                    )
                }
            }
            if (offline && pinned.isNotEmpty()) {
                EmptyState(
                    "Can't reach the TV server",
                    null,
                    listOf("Try again" to actions.retry, "Settings" to actions.openSettings),
                    Modifier.padding(start = px(116)),
                )
            }
            Spacer(Modifier.height(px(30)))
            BasicText("Apps", Modifier.padding(start = px(116)), style = AtomixType.rowHeader())
            AppsRow(
                apps = ui.apps,
                loader = loader,
                listState = appList,
                requesters = { pkg -> appFocus.getOrPut(pkg) { FocusRequester() } },
                onOpen = actions.openApp,
                onFocused = { app, i -> inApps = true; memory.remember(FocusTarget(ROW_APPS, app.packageName, i)) },
                onAllApps = actions.allApps,
                allAppsFocus = allAppsFocus,
                modifier = Modifier.onFocusChanged { if (it.hasFocus) inApps = true },
            )
        }
    }
}

/** Scrolls [list] to the target and focuses it; false when it can't be focused. */
private suspend fun focusIn(list: LazyListState, target: FocusTarget, requesters: Map<String, FocusRequester>): Boolean {
    if (list.layoutInfo.visibleItemsInfo.none { it.key == target.key }) list.scrollToItem(target.index)
    withFrameNanos { }
    return runCatching { requesters[target.key]?.requestFocus() ?: error("not composed") }.isSuccess
}
