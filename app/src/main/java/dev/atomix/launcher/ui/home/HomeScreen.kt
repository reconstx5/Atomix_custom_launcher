package dev.atomix.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import coil.ImageLoader
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.onAir
import dev.atomix.launcher.shell.EditKey
import dev.atomix.launcher.shell.EditOutcome
import dev.atomix.launcher.shell.EditSession
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.shell.HomeUi
import dev.atomix.launcher.shell.LocalTopBarFocus
import dev.atomix.launcher.ui.settings.SettingsSection
import dev.atomix.launcher.shell.restoresFocus
import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.RowOrder
import dev.atomix.launcher.ui.kit.EmptyState
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.ScreenHints
import dev.atomix.launcher.ui.kit.SectionHeading
import dev.atomix.launcher.ui.kit.TextAction
import dev.atomix.launcher.ui.kit.hintsFor
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

const val ROW_ON_NOW = "onNow"
const val ROW_APPS = "apps"

private const val ALL_APPS_KEY = "all-apps"

/** Room around the app tiles for the focus ring, its glow and the 12 px lift, which a scrolling row would clip. */
private const val GLOW_ROOM = 48

/** What Home asks its host to do. */
class HomeActions(
    val play: (Channel) -> Unit,
    val openApp: (AppEntry) -> Unit,
    val allApps: () -> Unit,
    val chooseChannels: () -> Unit,
    /** Opens Settings, at a section when the button is about one. */
    val openSettings: (SettingsSection?) -> Unit,
    val retry: () -> Unit,
    /** Editing "On now" ended with a new order. */
    val saveChannels: (List<String>) -> Unit,
    /** The "Your apps" row's new order, saved each time a tile is put down. */
    val saveApps: (List<String>) -> Unit,
)

/**
 * Home, one fixed page: "Your apps" (tiles, ending with "All apps") over "On now" (the first channel on Home as a
 * large card, the rest as small cards in a column that shows two). Each row's "Edit" rearranges it and nothing more.
 * Remembers focus and restores it whenever [restoreTick] changes (returning from an app, from playback, or switching
 * back to this tab).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    ui: HomeUi,
    loader: ImageLoader,
    url: (String?) -> String?,
    is24: Boolean,
    /** How Home is laid out (Settings › Home layout). */
    layout: HomeLayout,
    memory: FocusMemory,
    restoreTick: Int,
    actions: HomeActions,
) {
    // Every position and size below comes from here, as the preview in Settings does.
    val m = remember(layout) { homeMetrics(layout) }
    val area = onNowArea(ui.live)
    val pinned = (area as? OnNowArea.Cards)?.let { listOf(it.large) + it.small }.orEmpty()
    val bar = LocalTopBarFocus.current
    val topBar = bar?.marked
    val channelFocus = remember { HashMap<String, FocusRequester>() }
    val appFocus = remember { HashMap<String, FocusRequester>() }
    val allAppsFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    val appsEditFocus = remember { FocusRequester() }
    val channelsEditFocus = remember { FocusRequester() }
    val appList = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Edit mode: one row at a time. The row draws the session's order; keys are channel ids / package names.
    var editRow by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<EditSession?>(null) }
    var focusedId by remember { mutableStateOf(memory.current()?.takeIf { it.row == ROW_ON_NOW }?.key) }
    var focusedApp by remember { mutableStateOf(memory.current()?.takeIf { it.row == ROW_APPS }?.key) }
    // Which of the small cards is the upper of the two that show.
    var firstSmall by remember { mutableIntStateOf(0) }
    // Whether focus is on a channel card: only then can the cards just off the two-card window take focus.
    var inCards by remember { mutableStateOf(false) }
    val editing = edit != null
    val channelEdit = edit?.takeIf { editRow == ROW_ON_NOW }
    val appEdit = edit?.takeIf { editRow == ROW_APPS }
    val channelsShown = channelEdit?.order?.mapNotNull { id -> pinned.firstOrNull { it.id == id } } ?: pinned
    val appsShown = appEdit?.order?.mapNotNull { p -> ui.apps.firstOrNull { it.packageName == p } } ?: ui.apps
    val smalls = channelsShown.drop(1)
    val smallStart = firstSmall.coerceIn(0, max(smalls.size - 2, 0))
    val appsEditShown = appEdit == null && ui.apps.size >= 2
    val channelsEditShown = channelEdit == null && pinned.size >= 2
    val saveChannels by rememberUpdatedState(actions.saveChannels)
    val saveApps by rememberUpdatedState(actions.saveApps)

    fun appVisible(key: Any) = appList.layoutInfo.visibleItemsInfo.any { it.key == key }

    /** Where focus lands in the apps row from outside it: the tile last used, else the first one showing. */
    fun appEntry(): FocusRequester {
        focusedApp?.takeIf(::appVisible)?.let { appFocus[it] }?.let { return it }
        return when (val first = appList.layoutInfo.visibleItemsInfo.firstOrNull()?.key) {
            null -> FocusRequester.Default
            ALL_APPS_KEY -> allAppsFocus
            else -> appFocus[first] ?: FocusRequester.Default
        }
    }

    /** Where focus lands below the apps row: the large card, or the first button of a message. */
    fun onNowEntry(): FocusRequester = when (area) {
        is OnNowArea.Cards -> channelsShown.firstOrNull()?.let { channelFocus[it.id] } ?: FocusRequester.Default
        is OnNowArea.Message -> emptyFocus
        OnNowArea.Blank -> FocusRequester.Default
    }

    /** Brings small card [small] (of [count]) into the two that show; see [smallWindowStart]. */
    fun showSmall(small: Int, count: Int, page: Boolean) {
        firstSmall = smallWindowStart(firstSmall, small, count, page)
    }

    /** Brings a channel's card on screen and focuses it; false when it can't be. */
    suspend fun focusChannel(id: String, ids: List<String>): Boolean {
        val i = ids.indexOf(id)
        if (i < 0) return false
        if (i > 0) showSmall(i - 1, ids.size - 1, page = false)
        withFrameNanos { }
        return runCatching { channelFocus[id]?.requestFocus() ?: error("not composed") }.isSuccess
    }

    /** Scrolls the apps row to a tile and focuses it; false when it can't be. */
    suspend fun focusApp(pkg: String, index: Int): Boolean {
        if (!appVisible(pkg)) appList.scrollToItem(index)
        withFrameNanos { }
        return runCatching { appFocus[pkg]?.requestFocus() ?: error("not composed") }.isSuccess
    }

    fun startEdit(row: String) {
        if (edit != null) return
        val keys = if (row == ROW_ON_NOW) pinned.map { it.id } else ui.apps.map { it.packageName }
        if (keys.size < 2) return
        val key = (if (row == ROW_ON_NOW) focusedId else focusedApp)?.takeIf { it in keys } ?: keys.first()
        scope.launch {
            // Focus goes to the item first, and only then does "Edit" give way to the status text: a focused control
            // that disappears hands focus to the first thing on the page. If focus could not be put in the row,
            // editing does not start: OK must act on the row and nothing else while it is on.
            val inRow = if (row == ROW_ON_NOW) focusChannel(key, keys) else focusApp(key, keys.indexOf(key))
            if (!inRow) return@launch
            edit = EditSession(keys)
            editRow = row
        }
    }

    /**
     * Ends editing. Also called if focus leaves the row being edited, so OK never acts elsewhere mid-edit. With
     * [save] false what was arranged is dropped: the row no longer holds what the edit began with.
     */
    fun endEdit(save: Boolean = true) {
        val s = edit ?: return
        if (save) {
            if (editRow == ROW_ON_NOW && s.changed) saveChannels(s.order)
            // A tile still in the hand is put down where it is, as Back would have done.
            if (editRow == ROW_APPS && s.held != null) saveApps(s.order)
        }
        edit = null
        editRow = null
    }

    // While an app tile is held the row shows a fixed window of tiles (first index, how many fit), set at pick-up and
    // re-applied after every move. Otherwise the list keeps its first visible item in place, so the row jumps.
    val moveWindow = remember { IntArray(2) }

    fun holdWindow() {
        val info = appList.layoutInfo
        val end = info.viewportEndOffset - info.afterContentPadding
        moveWindow[0] = appList.firstVisibleItemIndex + if (appList.firstVisibleItemScrollOffset > 0) 1 else 0
        moveWindow[1] = info.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= end }.coerceAtLeast(1)
    }

    /** Keeps the held tile on screen as it moves, scrolling only when it reaches the window's edge. */
    fun keepInView(index: Int) {
        val count = moveWindow[1]
        moveWindow[0] = when {
            index < moveWindow[0] -> index
            index > moveWindow[0] + count - 1 -> index - count + 1
            else -> moveWindow[0]
        }
        // Applied in the same measure pass as the reorder, so the list never anchors to its old first item.
        appList.requestScrollToItem(moveWindow[0])
    }

    /** One remote key while [row] is being edited; false lets an arrow go on to move focus. */
    fun onEditKey(row: String, key: EditKey): Boolean {
        val s = edit ?: return false
        val focusedKey = if (row == ROW_ON_NOW) focusedId else focusedApp
        if (row == ROW_APPS && key == EditKey.Ok && s.held == null) holdWindow()
        return when (s.onKey(key, focusedKey)) {
            EditOutcome.Ignored -> false
            EditOutcome.Handled -> {
                val held = s.held
                // The held item keeps focus as it moves; the row follows it.
                val at = if (held == null) -1 else s.order.indexOf(held)
                if (row == ROW_ON_NOW && at > 0) showSmall(at - 1, s.order.size - 1, page = false)
                else if (row == ROW_APPS && at >= 0 && key != EditKey.Ok) keepInView(at)
                true
            }
            EditOutcome.Placed -> {
                if (row == ROW_APPS) saveApps(s.order)
                true
            }
            EditOutcome.Finished -> {
                endEdit()
                true
            }
        }
    }

    BackHandler(enabled = editing) { editRow?.let { onEditKey(it, EditKey.Back) } }
    // An edit stands only while its row still holds the items it began with. If a channel goes on or off Home
    // elsewhere, an app is uninstalled, or the cards give way to a message (server unreachable), the edit is over and
    // nothing of it is saved. This does not wait for focus to say so: the row may have gone, guard and all.
    val editedIds = when (editRow) {
        ROW_ON_NOW -> pinned.map { it.id }
        ROW_APPS -> ui.apps.map { it.packageName }
        else -> null
    }
    LaunchedEffect(edit, editedIds) {
        val s = edit
        if (s != null && editedIds != null && !s.fits(editedIds)) endEdit(save = false)
    }
    // Leaving Home mid-edit (another tab, the Home button) still saves what was arranged.
    DisposableEffect(Unit) { onDispose { endEdit() } }

    // Restore focus on every tick, and again whenever either row's contents change: an item that disappears while
    // Home is on screen (taken off Home on another device, app uninstalled) would otherwise take the focus with it.
    val restoredAt = remember { intArrayOf(Int.MIN_VALUE) }
    LaunchedEffect(restoreTick, pinned.size, ui.apps.size, area::class) {
        if (edit != null) return@LaunchedEffect
        // Coming to Home always restores. A list that only changed under it does not take focus from the top bar.
        if (!restoresFocus(byTick = restoredAt[0] != restoreTick, barHasFocus = bar?.hasFocus == true)) return@LaunchedEffect
        val rows = linkedMapOf(ROW_ON_NOW to pinned.map { it.id }, ROW_APPS to ui.apps.map { it.packageName })
        // With no cards the area holds a message, and its button is what to focus, on a first visit and again when
        // the last channel leaves Home, unless the user was last on an app tile.
        val rememberedApp = memory.current()?.row == ROW_APPS
        val target = if (pinned.isEmpty() && !rememberedApp) null else memory.resolve(rows)
        withFrameNanos { }
        val ok = when (target?.row) {
            ROW_ON_NOW -> focusChannel(target.key, pinned.map { it.id })
            ROW_APPS -> focusApp(target.key, target.index)
            else -> false
        }
        // While the first load is still running there is nothing worth focusing: an early OK would open an app
        // instead of the channel the user left.
        if (area is OnNowArea.Blank) return@LaunchedEffect
        // Only now has this visit been answered: a first load that had nothing to focus must still be restored.
        restoredAt[0] = restoreTick
        if (ok) return@LaunchedEffect
        runCatching { emptyFocus.requestFocus() }.recoverCatching { allAppsFocus.requestFocus() }
    }

    // Up and Down between the rows follow the order chosen in Settings: where focus lands in a row from outside it.
    fun entryOf(row: HomeRow): FocusRequester = if (row == HomeRow.Apps) appEntry() else onNowEntry()

    /** Where Up goes from [row]: the row above, or the top bar's marked item from the first row (null: leave it be). */
    fun upFrom(row: HomeRow): FocusRequester? = rowAbove(row, layout.rowOrder)?.let(::entryOf) ?: topBar

    /** Where Down goes from [row]: the row below; from the last row, nowhere. */
    fun downFrom(row: HomeRow): FocusRequester = rowBelow(row, layout.rowOrder)?.let(::entryOf) ?: FocusRequester.Cancel

    // Down from the top bar lands on the first row's remembered item, not on whichever item is nearest.
    val barDown by rememberUpdatedState { entryOf(rowsInOrder(layout.rowOrder).first()) }
    DisposableEffect(bar) {
        bar?.down = { barDown() }
        onDispose { bar?.down = null }
    }

    Box(Modifier.fillMaxSize()) {
        val appsAction: (@Composable () -> Unit)? = when {
            appEdit != null -> { -> EditStatus(appEdit) }
            appsEditShown -> { ->
                TextAction(
                    "Edit", LineIcon.Edit, { startEdit(ROW_APPS) },
                    Modifier.focusRequester(appsEditFocus).focusProperties {
                        if (!editing) {
                            if (appVisible(ALL_APPS_KEY)) down = allAppsFocus
                            // Under "On now", the nearest thing above is the lower small card; go to the cards' entry.
                            if (rowAbove(HomeRow.Apps, layout.rowOrder) != null) up = (smalls.getOrNull(smallStart + 1) ?: smalls.getOrNull(smallStart))?.let { channelFocus[it.id] } ?: onNowEntry()
                        }
                    },
                )
            }
            else -> null
        }
        SectionHeading("Your apps", Modifier.padding(start = px(100), end = px(100), top = px(m.appsHeadingY)), appsAction)
        LazyRow(
            Modifier.padding(top = px(m.appsY - GLOW_ROOM)).fillMaxWidth()
                .onFocusChanged { if (!it.hasFocus && editRow == ROW_APPS) endEdit() }
                .editKeys(appEdit != null) { onEditKey(ROW_APPS, it) }
                .focusProperties { if (appEdit != null) exit = { FocusRequester.Cancel } }
                .focusGroup(),
            state = appList,
            contentPadding = PaddingValues(horizontal = px(100), vertical = px(GLOW_ROOM)),
            horizontalArrangement = Arrangement.spacedBy(px(m.gap)),
        ) {
            itemsIndexed(appsShown, key = { _, a -> a.packageName }) { i, app ->
                HomeAppTile(
                    app, loader, m,
                    onClick = { actions.openApp(app) },
                    modifier = Modifier.width(px(m.tileW)).lifted(app.packageName == appEdit?.held)
                        .focusRequester(appFocus.getOrPut(app.packageName) { FocusRequester() })
                        .focusProperties {
                            // While editing, the arrows belong to the row: a fixed target would step past its edge.
                            if (!editing) {
                                upFrom(HomeRow.Apps)?.let { up = it }
                                down = downFrom(HomeRow.Apps)
                            }
                        },
                    onFocused = { focusedApp = app.packageName; memory.remember(FocusTarget(ROW_APPS, app.packageName, i)) },
                )
            }
            item(key = ALL_APPS_KEY) {
                HomeAllAppsTile(
                    m, actions.allApps,
                    Modifier.width(px(m.tileW)).focusRequester(allAppsFocus).focusProperties {
                        // Not an item of the row: it stays put, out of reach, while the row is rearranged.
                        canFocus = !editing
                        // The row ends here: Right must not wander up to "Edit".
                        right = FocusRequester.Cancel
                        if (!editing) {
                            if (appsEditShown) up = appsEditFocus else upFrom(HomeRow.Apps)?.let { up = it }
                            down = downFrom(HomeRow.Apps)
                        }
                    },
                )
            }
        }

        val channelsAction: (@Composable () -> Unit)? = when {
            channelEdit != null -> { -> EditStatus(channelEdit) }
            channelsEditShown -> { ->
                TextAction(
                    "Edit", LineIcon.Edit, { startEdit(ROW_ON_NOW) },
                    Modifier.focusRequester(channelsEditFocus).focusProperties {
                        if (!editing) {
                            // Under the apps row, Up is "All apps", the tile under this action; as the first row, the top bar.
                            if (rowAbove(HomeRow.OnNow, layout.rowOrder) != null && appVisible(ALL_APPS_KEY)) up = allAppsFocus
                            smalls.getOrNull(smallStart)?.let { channelFocus[it.id] }?.let { down = it }
                        }
                    },
                )
            }
            else -> null
        }
        SectionHeading("On now", Modifier.padding(start = px(100), end = px(100), top = px(m.onNowHeadingY)), channelsAction)
        Box(Modifier.padding(start = px(100), top = px(m.cardsY)).size(px(1720), px(m.cardsH))) {
            when (area) {
                OnNowArea.Blank -> Unit
                is OnNowArea.Message -> EmptyState(
                    area.title,
                    area.text,
                    area.actions.map { action ->
                        action.label to when (action) {
                            OnNowAction.OpenSettings, OnNowAction.Settings -> { -> actions.openSettings(action.settingsSection()) }
                            OnNowAction.Retry -> actions.retry
                            OnNowAction.ChooseChannels -> actions.chooseChannels
                        }
                    },
                    first = Modifier.focusRequester(emptyFocus),
                )
                is OnNowArea.Cards -> Box(
                    Modifier.fillMaxSize()
                        .onFocusChanged {
                            inCards = it.hasFocus
                            if (!it.hasFocus && editRow == ROW_ON_NOW) endEdit()
                        }
                        .editKeys(channelEdit != null) { onEditKey(ROW_ON_NOW, it) }
                        .focusProperties { if (channelEdit != null) exit = { FocusRequester.Cancel } }
                        .focusGroup(),
                ) {
                    val smallX = px(m.smallX)
                    // Every card is the same kind of card and is placed by where its channel is in the order. They are
                    // composed in an order that never changes (by channel id), so rearranging the row only changes
                    // where each one is put: no card is moved or rebuilt, and the held one keeps its focus. Of the
                    // small cards only the two that show are drawn; the one just above and the one just below are
                    // there, unseen, so that Up and Down have somewhere to go, and stepping onto one turns the page.
                    channelsShown.sortedBy { it.id }.forEach { ch ->
                        key(ch.id) {
                            val i = channelsShown.indexOf(ch)
                            val small = i - 1
                            val shown = i == 0 || small in smallStart..smallStart + 1
                            if (shown || small in smallStart - 1..smallStart + 2) {
                            val on = onAir(ch, ui.nowMs)
                            ChannelCard(
                                ch, onAirLines(on, is24), url(on.programme?.image), loader,
                                large = i == 0,
                                widthPx = if (i == 0) m.largeW else m.smallW,
                                heightPx = if (i == 0) m.cardsH else m.smallH,
                                onClick = { actions.play(ch) },
                                onFocused = {
                                    focusedId = ch.id
                                    if (i > 0) showSmall(small, channelsShown.size - 1, page = true)
                                    memory.remember(FocusTarget(ROW_ON_NOW, ch.id, i))
                                },
                                modifier = Modifier
                                    .offset(x = if (i == 0) px(0) else smallX, y = if (i == 0) px(0) else px((small - smallStart) * (m.smallH + CARD_GAP)))
                                    .graphicsLayer { alpha = if (shown) 1f else 0f }
                                    .lifted(ch.id == channelEdit?.held)
                                    .focusRequester(channelFocus.getOrPut(ch.id) { FocusRequester() })
                                    .focusProperties {
                                        canFocus = shown || inCards
                                        if (i == 0) {
                                            // Nothing is under the large card; the nearest thing below is an unseen
                                            // small card, off to the right.
                                            // Under the large card is the row below, or nothing; never the unseen
                                            // small card off to its right.
                                            down = if (editing) FocusRequester.Cancel else downFrom(HomeRow.OnNow)
                                            if (!editing) upFrom(HomeRow.OnNow)?.let { up = it }
                                        } else {
                                            if (small == 0 && channelsEditShown && !editing) up = channelsEditFocus
                                            // A key that arrives before the next unseen card has been built stops
                                            // here for a frame rather than finding something outside the cards.
                                            if (small > 0 && small == smallStart - 1) up = FocusRequester.Cancel
                                            if (small == smallStart + 2 && small < smalls.lastIndex) down = FocusRequester.Cancel
                                            // From the last small card Down goes on to the row below, when there is one.
                                            if (small == smalls.lastIndex && !editing) down = downFrom(HomeRow.OnNow)
                                        }
                                    },
                                watchHint = !editing,
                            )
                            }
                        }
                    }
                }
            }
        }
        ScreenHints(hintsFor(edit?.let { HintSpot.HomeEdit(holding = it.held != null) } ?: HintSpot.HomeRest))
    }
}
