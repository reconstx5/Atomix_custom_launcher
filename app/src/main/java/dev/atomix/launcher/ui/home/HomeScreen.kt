package dev.atomix.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import coil.ImageLoader
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.apps.InputEntry
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
import dev.atomix.launcher.prefs.OnNowLayout
import dev.atomix.launcher.prefs.SMALL_ROWS
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.ROW_ON_NOW
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowSort
import dev.atomix.launcher.shell.DrawnRow
import dev.atomix.launcher.shell.rowAbove
import dev.atomix.launcher.shell.rowBelow
import dev.atomix.launcher.ui.kit.LocalEffects
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    /** An app row's new order (row id, packages), saved each time a tile is put down. */
    val saveApps: (String, List<String>) -> Unit,
    /** Switches to an input from the Inputs row, as the Inputs screen does. */
    val openInput: (InputEntry) -> Unit = {},
)

/**
 * One app row's own things, kept per row id while Home is composed: its list, its tiles' focus, its last tile. The
 * Inputs row keeps the same, keyed by input id.
 */
private class AppRowState(focused: String?) {
    val list = LazyListState()
    val tiles = HashMap<String, FocusRequester>()
    val editFocus = FocusRequester()
    var focused by mutableStateOf(focused)
}

/**
 * Home: the rows of [HomeUi.rows] one under another, each where [rowPlaces] puts it. An app row is tiles (the first
 * app row drawn ends with "All apps"); "On now" is the first channel on Home as a large card and the rest as small
 * cards. Each row's "Edit" rearranges it and nothing more. When the rows are taller than the screen, Home scrolls a
 * row at a time ([scrollFor]) by moving all of them as one layer under the fixed top bar. Remembers focus and restores
 * it whenever [restoreTick] changes (returning from an app, from playback, or switching back to this tab).
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
    val rowKinds = ui.rows.map { it.spec.id to it.spec.kind }
    val places = remember(layout, rowKinds) { rowPlaces(layout, rowKinds) }
    // "On now" keeps phase 2's "On now first" height when it is the first row, its "Apps first" height anywhere else.
    val onNowFirst = ui.rows.firstOrNull()?.spec?.kind == RowKind.OnNow
    val mOnNow = remember(layout, onNowFirst) { homeMetrics(layout, onNowFirst) }
    val firstAppRow = ui.rows.firstOrNull { it.spec.kind == RowKind.Apps }?.spec?.id
    val area = onNowArea(ui.live)
    val pinned = (area as? OnNowArea.Cards)?.let { listOf(it.large) + it.small }.orEmpty()
    val bar = LocalTopBarFocus.current
    val topBar = bar?.marked
    val channelFocus = remember { HashMap<String, FocusRequester>() }
    val appRows = remember { HashMap<String, AppRowState>() }
    fun rowState(id: String): AppRowState = appRows.getOrPut(id) { AppRowState(memory.current()?.takeIf { it.row == id }?.key) }
    val allAppsFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    val channelsEditFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Edit mode: one row at a time, by row id. The row draws the session's order; keys are channel ids / package names.
    var editRow by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<EditSession?>(null) }
    var focusedId by remember { mutableStateOf(memory.current()?.takeIf { it.row == ROW_ON_NOW }?.key) }
    // Which row of small cards is the first of the two rows that show, as last asked for; read through firstRow.
    var firstRowWanted by remember { mutableIntStateOf(0) }
    // Whether focus is on a channel card: only then can the cards just off the rows that show take focus.
    var inCards by remember { mutableStateOf(false) }
    val editing = edit != null
    val channelEdit = edit?.takeIf { editRow == ROW_ON_NOW }
    val channelsShown = channelEdit?.order?.mapNotNull { id -> pinned.firstOrNull { it.id == id } } ?: pinned
    // The "On now" layout: where each channel's card goes, and which rows of small cards show.
    val onNow = layout.onNow
    val firstRow = firstRowFor(firstRowWanted, channelsShown.size, onNow)
    val lastRow = smallRowCount(channelsShown.size, onNow) - 1

    // The functions below answer "where does this key go?" at the moment a key is pressed, and are called from tiles
    // and cards that may have been built before the cards last moved or the rows last changed. So they read what is
    // on screen now from these, never from a value they were built with: a link worked out earlier could name a card
    // that has since gone out of view, and the key would do nothing (seen on the TV: Up from an app tile, with "Six
    // small" paged down).
    val shownNow by rememberUpdatedState(channelsShown)
    val areaNow by rememberUpdatedState(area)
    val layoutNow by rememberUpdatedState(layout)
    val rowsNow by rememberUpdatedState(ui.rows)
    fun idsNow(): List<String> = rowsNow.map { it.spec.id }
    fun kindOf(id: String): RowKind? = rowsNow.firstOrNull { it.spec.id == id }?.spec?.kind
    fun appsOf(id: String): List<AppEntry> = rowsNow.firstOrNull { it.spec.id == id }?.apps.orEmpty()
    fun firstRowNow(): Int = firstRowFor(firstRowWanted, shownNow.size, layoutNow.onNow)

    /** The focus handle of the small card at [row] and [column], or of the last card when that slot is empty; null with no small card. */
    fun smallAt(row: Int, column: Int): FocusRequester? {
        val index = min(indexAt(row, column, layoutNow.onNow), shownNow.lastIndex)
        if (index < 0 || slotOf(index, layoutNow.onNow) !is Slot.Small) return null
        return channelFocus[shownNow[index].id]
    }
    val channelsEditShown = channelEdit == null && pinned.size >= 2
    val saveChannels by rememberUpdatedState(actions.saveChannels)
    val saveApps by rememberUpdatedState(actions.saveApps)

    fun appVisible(rowId: String, key: Any) = rowState(rowId).list.layoutInfo.visibleItemsInfo.any { it.key == key }

    /** Where focus lands in an app row from outside it: the tile last used there, else the first one showing. */
    fun appEntry(rowId: String): FocusRequester {
        val st = rowState(rowId)
        st.focused?.takeIf { appVisible(rowId, it) }?.let { st.tiles[it] }?.let { return it }
        return when (val first = st.list.layoutInfo.visibleItemsInfo.firstOrNull()?.key) {
            null -> FocusRequester.Default
            ALL_APPS_KEY -> allAppsFocus
            else -> st.tiles[first] ?: FocusRequester.Default
        }
    }

    /** Where focus lands in "On now" from outside it: a card ([onNowEntryIndex]), or the first button of a message. */
    fun onNowEntry(): FocusRequester = when (areaNow) {
        is OnNowArea.Cards -> {
            val remembered = focusedId?.let { id -> shownNow.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            onNowEntryIndex(remembered, firstRowNow(), shownNow.size, layoutNow.onNow)?.let { channelFocus[shownNow[it].id] } ?: FocusRequester.Default
        }
        is OnNowArea.Message -> emptyFocus
        OnNowArea.Blank -> FocusRequester.Default
    }

    /** Where focus lands in the drawn row [id] from another row. */
    fun entryOf(id: String): FocusRequester = when (kindOf(id)) {
        RowKind.OnNow -> onNowEntry()
        RowKind.Apps, RowKind.Inputs -> appEntry(id)
        null -> FocusRequester.Default
    }

    /** Where Up goes from row [id]: the drawn row above, or the top bar's marked item from the first row (null: leave it be). */
    fun upFrom(id: String): FocusRequester? = rowAbove(id, idsNow())?.let(::entryOf) ?: topBar

    /** Where Down goes from row [id]: the drawn row below; from the last row, nowhere. */
    fun downFrom(id: String): FocusRequester = rowBelow(id, idsNow())?.let(::entryOf) ?: FocusRequester.Cancel

    /**
     * Brings the row of small cards holding channel [index] (of [channels]) into the two rows that show; see
     * [smallWindowStart]. The large card is always showing, so it moves nothing.
     */
    fun showCard(index: Int, channels: Int, page: Boolean) {
        val slot = slotOf(index, onNow) as? Slot.Small ?: return
        firstRowWanted = smallWindowStart(firstRowFor(firstRowWanted, channels, onNow), slot.row, smallRowCount(channels, onNow), page)
    }

    /** Brings a channel's card on screen and focuses it; false when it can't be. */
    suspend fun focusChannel(id: String, ids: List<String>): Boolean {
        val i = ids.indexOf(id)
        if (i < 0) return false
        showCard(i, ids.size, page = false)
        withFrameNanos { }
        return runCatching { channelFocus[id]?.requestFocus() ?: error("not composed") }.isSuccess
    }

    /** Scrolls app row [rowId] to a tile and focuses it; false when it can't be. */
    suspend fun focusApp(rowId: String, pkg: String, index: Int): Boolean {
        val st = rowState(rowId)
        if (!appVisible(rowId, pkg)) st.list.scrollToItem(index)
        withFrameNanos { }
        return runCatching { st.tiles[pkg]?.requestFocus() ?: error("not composed") }.isSuccess
    }

    fun startEdit(row: String) {
        if (edit != null) return
        val onNowRow = row == ROW_ON_NOW
        val keys = if (onNowRow) pinned.map { it.id } else appsOf(row).map { it.packageName }
        if (keys.size < 2) return
        val key = (if (onNowRow) focusedId else rowState(row).focused)?.takeIf { it in keys } ?: keys.first()
        scope.launch {
            // Focus goes to the item first, and only then does "Edit" give way to the status text: a focused control
            // that disappears hands focus to the first thing on the page. If focus could not be put in the row,
            // editing does not start: OK must act on the row and nothing else while it is on.
            val inRow = if (onNowRow) focusChannel(key, keys) else focusApp(row, key, keys.indexOf(key))
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
        val row = editRow
        if (save && row != null) {
            if (row == ROW_ON_NOW && s.changed) saveChannels(s.order)
            // A tile still in the hand is put down where it is, as Back would have done.
            if (row != ROW_ON_NOW && s.held != null) saveApps(row, s.order)
        }
        edit = null
        editRow = null
    }

    // While an app tile is held the row shows a fixed window of tiles (first index, how many fit), set at pick-up and
    // re-applied after every move. Otherwise the list keeps its first visible item in place, so the row jumps.
    val moveWindow = remember { IntArray(2) }

    fun holdWindow(list: LazyListState) {
        val info = list.layoutInfo
        val end = info.viewportEndOffset - info.afterContentPadding
        moveWindow[0] = list.firstVisibleItemIndex + if (list.firstVisibleItemScrollOffset > 0) 1 else 0
        moveWindow[1] = info.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= end }.coerceAtLeast(1)
    }

    /** Keeps the held tile on screen as it moves, scrolling only when it reaches the window's edge. */
    fun keepInView(list: LazyListState, index: Int) {
        val count = moveWindow[1]
        moveWindow[0] = when {
            index < moveWindow[0] -> index
            index > moveWindow[0] + count - 1 -> index - count + 1
            else -> moveWindow[0]
        }
        // Applied in the same measure pass as the reorder, so the list never anchors to its old first item.
        list.requestScrollToItem(moveWindow[0])
    }

    /** One remote key while [row] is being edited; false lets an arrow go on to move focus. */
    fun onEditKey(row: String, key: EditKey): Boolean {
        val s = edit ?: return false
        val onNowRow = row == ROW_ON_NOW
        val focusedKey = if (onNowRow) focusedId else rowState(row).focused
        if (!onNowRow && key == EditKey.Ok && s.held == null) holdWindow(rowState(row).list)
        return when (s.onKey(key, focusedKey)) {
            EditOutcome.Ignored -> false
            EditOutcome.Handled -> {
                val held = s.held
                // The held item keeps focus as it moves; the row follows it.
                val at = if (held == null) -1 else s.order.indexOf(held)
                if (onNowRow && at >= 0) showCard(at, s.order.size, page = false)
                else if (!onNowRow && at >= 0 && key != EditKey.Ok) keepInView(rowState(row).list, at)
                true
            }
            EditOutcome.Placed -> {
                if (!onNowRow) saveApps(row, s.order)
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
    // elsewhere, an app is uninstalled, the row goes, or the cards give way to a message (server unreachable), the
    // edit is over and nothing of it is saved. This does not wait for focus to say so: the row may have gone, guard and all.
    val editedIds = when (val row = editRow) {
        null -> null
        ROW_ON_NOW -> pinned.map { it.id }
        else -> ui.rows.firstOrNull { it.spec.id == row }?.apps.orEmpty().map { it.packageName }
    }
    LaunchedEffect(edit, editedIds) {
        val s = edit
        if (s != null && editedIds != null && !s.fits(editedIds)) endEdit(save = false)
    }
    // Leaving Home mid-edit (another tab, the Home button) still saves what was arranged.
    DisposableEffect(Unit) { onDispose { endEdit() } }

    // Restore focus on every tick, and again whenever a row's contents change: an item that disappears while Home is
    // on screen (taken off Home on another device, app uninstalled) would otherwise take the focus with it.
    val restoredAt = remember { intArrayOf(Int.MIN_VALUE) }
    val rowSizes = ui.rows.map { it.spec.id to if (it.spec.kind == RowKind.Inputs) ui.inputs.size else it.apps.size }
    LaunchedEffect(restoreTick, pinned.size, rowSizes, area::class) {
        if (edit != null) return@LaunchedEffect
        // Coming to Home always restores. A list that only changed under it does not take focus from the top bar.
        if (!restoresFocus(byTick = restoredAt[0] != restoreTick, barHasFocus = bar?.hasFocus == true)) return@LaunchedEffect
        // In Home's order, so a remembered item that has gone falls back to the first item of the first drawn row.
        val rows = LinkedHashMap<String, List<String>>()
        ui.rows.forEach { r ->
            rows[r.spec.id] = when (r.spec.kind) {
                RowKind.OnNow -> pinned.map { it.id }
                RowKind.Inputs -> ui.inputs.map { it.id }
                RowKind.Apps -> r.apps.map { it.packageName }
            }
        }
        // With no cards the area holds a message, and its button is what to focus, on a first visit and again when
        // the last channel leaves Home, unless the user was last on an app tile.
        val rememberedApp = memory.current()?.row?.let { it != ROW_ON_NOW } == true
        val target = if (pinned.isEmpty() && !rememberedApp) null else memory.resolve(rows)
        withFrameNanos { }
        val ok = when {
            target == null -> false
            target.row == ROW_ON_NOW -> focusChannel(target.key, pinned.map { it.id })
            else -> focusApp(target.row, target.key, target.index)
        }
        // While the first load is still running there is nothing worth focusing: an early OK would open an app
        // instead of the channel the user left.
        if (area is OnNowArea.Blank) return@LaunchedEffect
        // Only now has this visit been answered: a first load that had nothing to focus must still be restored.
        restoredAt[0] = restoreTick
        // Only from here on is focus the user's: until now it may be the new window's own first focus.
        memory.restored(target.takeIf { ok })
        if (ok) return@LaunchedEffect
        runCatching { emptyFocus.requestFocus() }.recoverCatching { allAppsFocus.requestFocus() }
    }

    // Down from the top bar lands on the first row's remembered item, not on whichever item is nearest.
    val barDown by rememberUpdatedState { idsNow().firstOrNull()?.let(::entryOf) ?: FocusRequester.Default }
    DisposableEffect(bar) {
        bar?.down = { barDown() }
        onDispose { bar?.down = null }
    }

    // Scrolling: the row that holds focus is brought whole into view, as one layer under the top bar.
    val unit = with(LocalDensity.current) { px(1).toPx() }
    val reduced = LocalEffects.current.reduced
    val scroll = remember { Animatable(0f) }
    var focusRow by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusRow, places) {
        val i = places.indexOfFirst { it.id == focusRow }
        if (i < 0) return@LaunchedEffect
        val target = scrollFor(scroll.targetValue.roundToInt(), places, i).toFloat()
        if (target == scroll.targetValue) return@LaunchedEffect
        if (reduced) scroll.snapTo(target) else scroll.animateTo(target, tween(250, easing = FastOutSlowInEasing))
    }

    @Composable
    fun AppRow(row: DrawnRow, place: RowPlace) {
        val id = row.spec.id
        val st = rowState(id)
        val appEdit = edit?.takeIf { editRow == id }
        val appsShown = appEdit?.order?.mapNotNull { p -> row.apps.firstOrNull { it.packageName == p } } ?: row.apps
        val hasAllApps = id == firstAppRow
        // With "A to Z" the order is automatic, so there is nothing to rearrange.
        val editShown = appEdit == null && row.apps.size >= 2 && row.spec.sort == RowSort.YourOrder
        val action: (@Composable () -> Unit)? = when {
            appEdit != null -> { -> EditStatus(appEdit) }
            editShown -> { ->
                TextAction(
                    "Edit", LineIcon.Edit, { startEdit(id) },
                    Modifier.focusRequester(st.editFocus).focusProperties {
                        if (!editing) {
                            if (hasAllApps && appVisible(id, ALL_APPS_KEY)) down = allAppsFocus
                            // Under "On now", the nearest thing above is the lower small card; go to the cards' entry.
                            when (val above = rowAbove(id, idsNow())) {
                                null -> Unit
                                ROW_ON_NOW -> up = smallAt(firstRowNow() + 1, layoutNow.onNow.columns - 1) ?: onNowEntry()
                                else -> up = entryOf(above)
                            }
                        }
                    },
                )
            }
            else -> null
        }
        SectionHeading(row.spec.name, Modifier.padding(start = px(100), end = px(100)), action)
        LazyRow(
            Modifier.padding(top = px(place.y - place.headingY - GLOW_ROOM)).fillMaxWidth()
                .onFocusChanged { if (!it.hasFocus && editRow == id) endEdit() }
                .editKeys(appEdit != null) { onEditKey(id, it) }
                .focusProperties { if (appEdit != null) exit = { FocusRequester.Cancel } }
                .focusGroup(),
            state = st.list,
            contentPadding = PaddingValues(horizontal = px(100), vertical = px(GLOW_ROOM)),
            horizontalArrangement = Arrangement.spacedBy(px(m.gap)),
        ) {
            itemsIndexed(appsShown, key = { _, a -> a.packageName }) { i, app ->
                HomeAppTile(
                    app, loader, m,
                    onClick = { actions.openApp(app) },
                    modifier = Modifier.width(px(m.tileW)).lifted(app.packageName == appEdit?.held)
                        .focusRequester(st.tiles.getOrPut(app.packageName) { FocusRequester() })
                        .focusProperties {
                            // While editing, the arrows belong to the row: a fixed target would step past its edge.
                            if (!editing) {
                                upFrom(id)?.let { up = it }
                                down = downFrom(id)
                            }
                        },
                    onFocused = { st.focused = app.packageName; memory.remember(FocusTarget(id, app.packageName, i)) },
                )
            }
            if (hasAllApps) item(key = ALL_APPS_KEY) {
                HomeAllAppsTile(
                    m, actions.allApps,
                    Modifier.width(px(m.tileW)).focusRequester(allAppsFocus).focusProperties {
                        // Not an item of the row: it stays put, out of reach, while the row is rearranged.
                        canFocus = !editing
                        // The row ends here: Right must not wander up to "Edit".
                        right = FocusRequester.Cancel
                        if (!editing) {
                            if (editShown) up = st.editFocus else upFrom(id)?.let { up = it }
                            down = downFrom(id)
                        }
                    },
                )
            }
        }
    }

    /**
     * The Inputs row (spec §6.4): the TV's HDMI ports, then the tuner, as tiles of Home's app-tile size. It has no
     * "Edit": its order is the TV's. OK switches to the input; focus is remembered as on an app row, so coming back
     * from the input lands on its tile.
     */
    @Composable
    fun InputsRow(row: DrawnRow, place: RowPlace) {
        val id = row.spec.id
        val st = rowState(id)
        SectionHeading(row.spec.name.ifEmpty { "Inputs" }, Modifier.padding(start = px(100), end = px(100)), null)
        LazyRow(
            Modifier.padding(top = px(place.y - place.headingY - GLOW_ROOM)).fillMaxWidth().focusGroup(),
            state = st.list,
            contentPadding = PaddingValues(horizontal = px(100), vertical = px(GLOW_ROOM)),
            horizontalArrangement = Arrangement.spacedBy(px(m.gap)),
        ) {
            itemsIndexed(ui.inputs, key = { _, input -> input.id }) { i, input ->
                InputTile(
                    input, m,
                    onClick = { actions.openInput(input) },
                    modifier = Modifier.width(px(m.tileW))
                        .focusRequester(st.tiles.getOrPut(input.id) { FocusRequester() })
                        .focusProperties {
                            if (!editing) {
                                upFrom(id)?.let { up = it }
                                down = downFrom(id)
                            }
                            if (i == ui.inputs.lastIndex) right = FocusRequester.Cancel
                        },
                    onFocused = { st.focused = input.id; memory.remember(FocusTarget(id, input.id, i)) },
                )
            }
        }
    }

    @Composable
    fun OnNowRow(place: RowPlace) {
        val channelsAction: (@Composable () -> Unit)? = when {
            channelEdit != null -> { -> EditStatus(channelEdit) }
            channelsEditShown -> { ->
                TextAction(
                    "Edit", LineIcon.Edit, { startEdit(ROW_ON_NOW) },
                    Modifier.focusRequester(channelsEditFocus).focusProperties {
                        if (!editing) {
                            // Under an app row, Up is its "All apps" (the tile under this action) or its entry; as the
                            // first row, the top bar.
                            rowAbove(ROW_ON_NOW, idsNow())?.let { above ->
                                if (above == firstAppRow && appVisible(above, ALL_APPS_KEY)) up = allAppsFocus
                                else if (kindOf(above) == RowKind.Apps) up = entryOf(above)
                            }
                            // Down is the small card under this action: the last one of the first row that shows.
                            smallAt(firstRowNow(), layoutNow.onNow.columns - 1)?.let { down = it }
                        }
                    },
                )
            }
            else -> null
        }
        SectionHeading("On now", Modifier.padding(start = px(100), end = px(100)), channelsAction)
        Box(Modifier.padding(start = px(100), top = px(place.y - place.headingY)).size(px(1720), px(place.h))) {
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
                    // Every card is the same kind of card and is placed by where its channel is in the order. They are
                    // composed in an order that never changes (by channel id), so rearranging the row only changes
                    // where each one is put: no card is moved or rebuilt, and the held one keeps its focus. Of the
                    // small cards only the two rows that show are drawn; the row just above and the row just below
                    // are there, unseen, so that Up and Down have somewhere to go, and stepping onto one moves the
                    // rows that show (two at a time in the one-column layout, a row at a time in the grids).
                    val largeFocus = if (onNow.large) channelsShown.firstOrNull()?.let { channelFocus[it.id] } else null
                    channelsShown.sortedBy { it.id }.forEach { ch ->
                        key(ch.id) {
                            val i = channelsShown.indexOf(ch)
                            val slot = slotOf(i, onNow)
                            val small = slot as? Slot.Small
                            if (isBuilt(slot, firstRow)) {
                            val cardAt = cardPlace(slot, firstRow, mOnNow)
                            val on = onAir(ch, ui.nowMs)
                            ChannelCard(
                                ch, onAirLines(on, is24), url(on.programme?.image), loader,
                                large = small == null,
                                widthPx = cardAt.w,
                                heightPx = cardAt.h,
                                onClick = { actions.play(ch) },
                                onFocused = {
                                    focusedId = ch.id
                                    showCard(i, channelsShown.size, page = onNow == OnNowLayout.LargeTwo)
                                    memory.remember(FocusTarget(ROW_ON_NOW, ch.id, i))
                                },
                                modifier = Modifier
                                    .offset(x = px(cardAt.x), y = px(cardAt.y))
                                    .graphicsLayer { alpha = if (cardAt.shown) 1f else 0f }
                                    .lifted(ch.id == channelEdit?.held)
                                    .focusRequester(channelFocus.getOrPut(ch.id) { FocusRequester() })
                                    .focusProperties {
                                        canFocus = cardAt.shown || inCards
                                        if (small == null) {
                                            // Under the large card is the row below, or nothing; never the unseen
                                            // small card off to its right.
                                            down = if (editing) FocusRequester.Cancel else downFrom(ROW_ON_NOW)
                                            if (!editing) upFrom(ROW_ON_NOW)?.let { up = it }
                                        } else {
                                            if (small.row == 0 && !editing) {
                                                if (channelsEditShown) up = channelsEditFocus else upFrom(ROW_ON_NOW)?.let { up = it }
                                            }
                                            // A key that arrives before the next unseen row has been built stops
                                            // here for a frame rather than finding something outside the cards.
                                            if (small.row > 0 && small.row == firstRow - 1) up = FocusRequester.Cancel
                                            if (small.row == firstRow + SMALL_ROWS && small.row < lastRow) down = FocusRequester.Cancel
                                            // From the last row Down goes on to the row of Home below, when there is one.
                                            if (small.row == lastRow && !editing) down = downFrom(ROW_ON_NOW)
                                            // A row of cards ends at its last card; its first card is beside the large
                                            // card, when there is one.
                                            if (small.column == onNow.columns - 1 || i == channelsShown.lastIndex) right = FocusRequester.Cancel
                                            if (small.column == 0) left = largeFocus ?: FocusRequester.Cancel
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
        // A sign that channels sit beyond the last card showing: one line under the cards, level with the small
        // cards. Not while a row is being rearranged (the hint bar is there then), nor under a message.
        val more = if (area is OnNowArea.Cards && !editing) moreChannelsLine(channelsShown.size, lastShownIndex(firstRow, channelsShown.size, onNow)) else null
        val moreY = place.moreY
        if (more != null && moreY != null) {
            Row(
                Modifier.padding(start = px(100 + mOnNow.smallX), top = px(moreY - place.headingY)).height(px(30)),
                horizontalArrangement = Arrangement.spacedBy(px(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(more, style = AtomixType.description(), maxLines = 1)
                Icon(LineIcon.ChevronDown, AtomixColors.Label, 24)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Rows pass under the top bar: once Home has scrolled they are cut off just below it.
        Box(
            Modifier.fillMaxSize().drawWithContent {
                if (scroll.value > 0f) clipRect(top = VIEW_TOP * unit) { this@drawWithContent.drawContent() } else drawContent()
            },
        ) {
            // As tall as its rows, not the screen: a row placed below the screen's foot must still be measured whole,
            // or it is squeezed to what is left of 1080 before the scroll brings it up.
            Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer { translationY = -scroll.value * unit }) {
                // Composed in an order that never changes (by id), so moving a row only moves where it is put.
                val placeOf = places.associateBy { it.id }
                ui.rows.sortedBy { it.spec.id }.forEach { row ->
                    val place = placeOf[row.spec.id] ?: return@forEach
                    key(row.spec.id) {
                        Box(
                            Modifier.padding(top = px(place.headingY)).fillMaxWidth()
                                .onFocusChanged { if (it.hasFocus) focusRow = row.spec.id },
                        ) {
                            when (row.spec.kind) {
                                RowKind.Apps -> AppRow(row, place)
                                RowKind.OnNow -> OnNowRow(place)
                                RowKind.Inputs -> InputsRow(row, place)
                            }
                        }
                    }
                }
            }
        }
        ScreenHints(hintsFor(edit?.let { HintSpot.HomeEdit(holding = it.held != null) } ?: HintSpot.HomeRest))
    }
}
