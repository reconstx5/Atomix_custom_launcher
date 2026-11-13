package dev.atomix.launcher.ui.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import dev.atomix.launcher.shell.EditSession
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.shell.HomeUi
import dev.atomix.launcher.ui.kit.ChipButton
import dev.atomix.launcher.ui.kit.EmptyState
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.LocalEffects
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    /** Edit mode ended on the "On now" row with a new order; ids left out are unpinned. */
    val saveChannels: (List<String>) -> Unit,
    /** The apps row's new order, saved as each move is placed or app removed. */
    val saveApps: (List<String>) -> Unit,
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
    val scope = rememberCoroutineScope()

    // Edit mode: one row at a time. The row draws the session's order; keys are channel ids / package names.
    var editRow by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<EditSession?>(null) }
    var onTrailing by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf(memory.current()?.takeIf { it.row == ROW_ON_NOW }?.key) }
    var focusedApp by remember { mutableStateOf(memory.current()?.takeIf { it.row == ROW_APPS }?.key) }
    val trailingFocus = remember { FocusRequester() }
    val channelEdit = edit?.takeIf { editRow == ROW_ON_NOW }
    val appEdit = edit?.takeIf { editRow == ROW_APPS }
    val channelsShown = channelEdit?.order?.mapNotNull { id -> pinned.firstOrNull { it.id == id } } ?: pinned
    val appsShown = appEdit?.order?.mapNotNull { p -> ui.apps.firstOrNull { it.packageName == p } } ?: ui.apps
    val saveChannels by rememberUpdatedState(actions.saveChannels)
    val saveApps by rememberUpdatedState(actions.saveApps)

    fun focusKey(row: String, key: String) {
        val s = edit ?: return
        val target = FocusTarget(row, key, s.order.indexOf(key).coerceAtLeast(0))
        scope.launch {
            withFrameNanos { }
            if (row == ROW_ON_NOW) focusIn(channelList, target, channelFocus) else focusIn(appList, target, appFocus)
        }
    }

    fun startEdit(row: String) {
        val keys = if (row == ROW_ON_NOW) pinned.map { it.id } else ui.apps.map { it.packageName }
        if (keys.isEmpty()) return
        edit = EditSession(keys)
        editRow = row
        onTrailing = false
        focusKey(row, (if (row == ROW_ON_NOW) focusedId else focusedApp)?.takeIf { it in keys } ?: keys.first())
    }

    fun endEdit() {
        val s = edit ?: return
        if (editRow == ROW_ON_NOW && s.changed) saveChannels(s.order)
        edit = null
        editRow = null
        onTrailing = false
    }

    // While an item is held the row shows a fixed window of items (first index, how many fit), set at pick-up and
    // re-applied after every move. Otherwise the list keeps its first visible item in place, so the row jumps.
    val moveWindow = remember { IntArray(2) }

    fun holdWindow(row: String) {
        val list = if (row == ROW_ON_NOW) channelList else appList
        val info = list.layoutInfo
        val end = info.viewportEndOffset - info.afterContentPadding
        moveWindow[0] = list.firstVisibleItemIndex + if (list.firstVisibleItemScrollOffset > 0) 1 else 0
        moveWindow[1] = info.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= end }.coerceAtLeast(1)
    }

    /** Keeps the held item on screen as it moves, scrolling only when it reaches the window's edge. */
    fun keepInView(row: String, index: Int) {
        val list = if (row == ROW_ON_NOW) channelList else appList
        val count = moveWindow[1]
        moveWindow[0] = when {
            index < moveWindow[0] -> index
            index > moveWindow[0] + count - 1 -> index - count + 1
            else -> moveWindow[0]
        }
        // Applied in the same measure pass as the reorder, so the list never anchors to its old first item.
        list.requestScrollToItem(moveWindow[0])
    }

    fun onEditKey(row: String, code: Int): Boolean {
        val s = edit ?: return false
        val key = if (row == ROW_ON_NOW) focusedId else focusedApp
        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> true // stay in the row being edited
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val held = s.held ?: return false
                s.move(if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                keepInView(row, s.order.indexOf(held))
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (onTrailing || key == null || key !in s.order) return true
                val i = s.order.indexOf(key)
                s.remove(key)
                if (row == ROW_APPS) saveApps(s.order)
                val next = s.order.getOrNull(i) ?: s.order.getOrNull(i - 1)
                if (next != null) focusKey(row, next)
                else scope.launch { withFrameNanos { }; runCatching { trailingFocus.requestFocus() } }
                true
            }
            else -> { // OK
                if (onTrailing) return false
                if (s.held == null) {
                    if (key != null) { holdWindow(row); s.pickUp(key) }
                } else {
                    s.place()
                    if (row == ROW_APPS) saveApps(s.order)
                }
                true
            }
        }
    }

    val channelKeys = remember(editRow) { if (editRow == ROW_ON_NOW) EditKeys { onEditKey(ROW_ON_NOW, it) } else null }
    val appKeys = remember(editRow) { if (editRow == ROW_APPS) EditKeys { onEditKey(ROW_APPS, it) } else null }
    BackHandler(enabled = edit != null) {
        val s = edit
        if (s?.held != null) s.cancelMove() else endEdit()
    }
    // Leaving Home mid-edit (another tab, the Home button) still saves what was arranged.
    DisposableEffect(Unit) { onDispose { endEdit() } }

    // The spotlight follows card focus once it settles (300 ms).
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
    // Slid up, the spotlight would run into the top menu; it fades out while the apps row has focus.
    val spotAlpha by animateFloatAsState(if (inApps) 0f else 1f, tween(if (reduced) 0 else 200), label = "spotlight")

    Box(Modifier.fillMaxSize()) {
        SpotlightBackdrop(art, dim = inApps)
        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer { translationY = offset }) {
            Box(Modifier.fillMaxWidth().height(px(616)).graphicsLayer { alpha = spotAlpha }.padding(start = px(116), end = px(116), bottom = px(8)), contentAlignment = Alignment.BottomStart) {
                Spotlight(spot, spotOn, url(spot?.logo), loader, is24)
            }
            EditableHeader(
                "On now",
                channelEdit,
                canEdit = edit == null && live is LiveState.Ready && pinned.isNotEmpty(),
                onEdit = { startEdit(ROW_ON_NOW) },
                Modifier.padding(start = px(116), top = px(24)).onFocusChanged { if (it.hasFocus) inApps = false },
            )
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
                        channels = channelsShown,
                        nowMs = ui.nowMs,
                        dimmed = offline,
                        loader = loader,
                        logoUrl = { url(it.logo) },
                        is24 = is24,
                        listState = channelList,
                        requesters = { id -> channelFocus.getOrPut(id) { FocusRequester() } },
                        onPlay = { if (offline) actions.unavailable() else actions.play(it) },
                        onFocused = { ch, i -> focusedId = ch.id; onTrailing = false; inApps = false; memory.remember(FocusTarget(ROW_ON_NOW, ch.id, i)) },
                        modifier = Modifier.editKeys(channelKeys),
                        held = channelEdit?.held,
                        trailing = if (channelEdit == null) null else {
                            { ChipButton("Pin a channel", { endEdit(); actions.chooseChannels() }, Modifier.focusRequester(trailingFocus).onFocusChanged { onTrailing = it.isFocused }, icon = LineIcon.Plus) }
                        },
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
            EditableHeader("Apps", appEdit, canEdit = false, onEdit = {}, Modifier.padding(start = px(116)))
            AppsRow(
                apps = appsShown,
                loader = loader,
                listState = appList,
                requesters = { pkg -> appFocus.getOrPut(pkg) { FocusRequester() } },
                onOpen = actions.openApp,
                onFocused = { app, i -> focusedApp = app.packageName; onTrailing = false; inApps = true; memory.remember(FocusTarget(ROW_APPS, app.packageName, i)) },
                onAllApps = actions.allApps,
                allAppsFocus = allAppsFocus,
                modifier = Modifier.onFocusChanged { if (it.hasFocus) inApps = true }.editKeys(appKeys),
                held = appEdit?.held,
                trailing = if (appEdit == null) null else {
                    { ChipButton("Add an app", { endEdit(); actions.allApps() }, Modifier.focusRequester(trailingFocus).height(px(88)).onFocusChanged { onTrailing = it.isFocused }, icon = LineIcon.Plus) }
                },
                onEdit = if (edit == null && ui.apps.isNotEmpty()) ({ startEdit(ROW_APPS) }) else null,
            )
        }
        edit?.let { EditHints(it, if (editRow == ROW_ON_NOW) "Unpin" else "Remove", Modifier.align(Alignment.BottomCenter)) }
    }
}

/** Scrolls [list] to the target and focuses it; false when it can't be focused. */
private suspend fun focusIn(list: LazyListState, target: FocusTarget, requesters: Map<String, FocusRequester>): Boolean {
    if (list.layoutInfo.visibleItemsInfo.none { it.key == target.key }) list.scrollToItem(target.index)
    withFrameNanos { }
    return runCatching { requesters[target.key]?.requestFocus() ?: error("not composed") }.isSuccess
}
