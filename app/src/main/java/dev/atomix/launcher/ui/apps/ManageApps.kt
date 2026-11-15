@file:OptIn(ExperimentalComposeUiApi::class) // FocusRequester.Cancel

package dev.atomix.launcher.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.ui.kit.AppArt
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.MENU_W
import dev.atomix.launcher.ui.kit.menuHeight
import dev.atomix.launcher.ui.kit.menuTop
import dev.atomix.launcher.ui.kit.textRoom
import dev.atomix.launcher.ui.theme.LookState
import dev.atomix.launcher.ui.kit.ScreenFrame
import dev.atomix.launcher.ui.kit.SegmentedPills
import dev.atomix.launcher.ui.kit.WindowColumn
import dev.atomix.launcher.ui.kit.WindowState
import dev.atomix.launcher.ui.kit.hintsFor
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp
import kotlinx.coroutines.launch

private const val ROWS_SHOWN = 4
private const val TILE_W = 232
private const val TILE_H = 140
private const val GAP = 16

/** The grid starts under the pills: 60 of track and 24 of space. */
private const val GRID_TOP = 84
private const val MENU_ITEM_H = 72

/**
 * Manage apps (spec §8): for one app row ([AppsTabState.forRow], "Choose apps for “Tools”") or for none ("Manage
 * apps"). OK on an app opens its menu: add to or remove from the row (or "Add to a row…", a short list of the app rows
 * with ticks, when it is for none), hide or show, and the TV's own App info page; Back closes the list, then the menu.
 * [onChanged] is told when a row or the hidden apps change. [onBack], when given, is where Back goes with no menu open
 * (Settings › Rows and apps, when opened from there), named by [back] in the hints.
 */
@Composable
fun ManageAppsScreen(
    state: AppsTabState,
    loader: ImageLoader,
    onInfo: (AppEntry) -> Unit,
    onChanged: () -> Unit,
    onBack: (() -> Unit)? = null,
    back: String = "Apps",
) {
    val apps by state.apps.collectAsState()
    // Bumped when the row changes: what is in the row is not observable, so the ticks and counts are re-read on it.
    var version by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(AppFilter.All) }
    val shown = remember(apps, filter, version) { state.shown(filter) }
    val rows = remember(shown) { shown.chunked(APP_COLUMNS) }
    val filters = remember(apps, version, state.forRow) { state.filters() }
    val pills = remember(filters, apps, version) { filters.map(state::pillLabel) }
    val window = remember(filter) { WindowState() }
    val tileFocus = remember { HashMap<String, FocusRequester>() }
    val pillFocus = remember { FocusRequester() }
    val menuFirst = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var onPill by remember { mutableStateOf(false) }
    var menuPkg by remember { mutableStateOf<String?>(null) }
    var menuItem by remember { mutableStateOf<AppMenuItem?>(null) }
    var refocus by remember { mutableStateOf<String?>(null) }
    // The "Add to a row…" list, open in the menu's place; [rowLine] is the row whose line has focus.
    var rowList by remember { mutableStateOf(false) }
    var rowLine by remember { mutableStateOf<String?>(null) }
    val menuApp = shown.firstOrNull { it.packageName == menuPkg }
    val menuItems = remember(menuPkg, apps, version) { menuPkg?.let(state::menuFor).orEmpty() }
    val ticks = remember(menuPkg, apps, version, rowList) { if (rowList) menuPkg?.let(state::rowTicks).orEmpty() else emptyList() }

    // The screen opens on its first app.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        shown.firstOrNull()?.let { runCatching { tileFocus[it.packageName]?.requestFocus() } }
    }

    /**
     * Closes the menu. Focus is handed to [to] first (the menu's own tile, unless that tile is about to go): a
     * focused control that is removed would give focus to the first thing on the page.
     */
    fun closeMenu(to: String? = menuPkg) {
        val tile = to?.let { tileFocus[it] }
        if (tile == null || runCatching { tile.requestFocus() }.isFailure) runCatching { pillFocus.requestFocus() }
        menuPkg = null
        menuItem = null
        rowList = false
        rowLine = null
    }

    /**
     * Swaps the menu for the "Add to a row…" list, or back. Focus waits on the menu's tile while one is taken away and
     * the other drawn, then goes to the new one's first line.
     */
    fun showRows(show: Boolean) {
        menuPkg?.let { tileFocus[it] }?.let { runCatching { it.requestFocus() } }
        rowList = show
        rowLine = null
        menuItem = null
        scope.launch { withFrameNanos { }; runCatching { menuFirst.requestFocus() } }
    }

    fun openMenu(app: AppEntry) {
        if (state.menuFor(app.packageName).isEmpty()) return
        menuPkg = app.packageName
        scope.launch { withFrameNanos { }; runCatching { menuFirst.requestFocus() } }
    }

    fun choose(item: AppMenuItem, app: AppEntry) {
        when (item) {
            AppMenuItem.Info -> onInfo(app)
            AppMenuItem.AddToARow -> showRows(true)
            is AppMenuItem.AddTo, is AppMenuItem.RemoveFrom -> {
                // Under "In this row" a removed app's tile goes away, so focus goes first to a neighbour that stays put.
                val leaving = item is AppMenuItem.RemoveFrom && filter == AppFilter.InRow
                val row = if (item is AppMenuItem.AddTo) item.row else (item as AppMenuItem.RemoveFrom).row
                closeMenu(if (leaving) focusAfterRemove(shown.map { it.packageName }, shown.indexOf(app), APP_COLUMNS) else app.packageName)
                state.toggle(app.packageName, row)
                version++
                onChanged()
            }
            AppMenuItem.Hide, AppMenuItem.Show -> {
                // The tile leaves the list (under a filter) or moves within it (under "All": hidden apps go last), so
                // focus goes first to a neighbour that stays put; under "All" it follows the app to its new place.
                val pkgs = shown.map { it.packageName }
                closeMenu(focusAfterRemove(pkgs, pkgs.indexOf(app.packageName), APP_COLUMNS) ?: app.packageName)
                state.toggleHidden(app.packageName)
                version++
                if (filter == AppFilter.All) refocus = app.packageName
                onChanged()
            }
        }
    }

    // After hiding or showing under "All", focus the app where it now is, once its tile has been drawn there.
    LaunchedEffect(refocus) {
        val pkg = refocus ?: return@LaunchedEffect
        val at = shown.indexOfFirst { it.packageName == pkg }
        if (at >= 0) {
            withFrameNanos { }
            runCatching { tileFocus[pkg]?.requestFocus() }
        }
        refocus = null
    }

    // An app that is uninstalled while its menu is open takes the menu with it.
    LaunchedEffect(menuPkg, menuItems) {
        if (menuPkg != null && menuItems.isEmpty()) closeMenu(shown.firstOrNull()?.packageName)
    }
    BackHandler(enabled = onBack != null && menuPkg == null) { onBack?.invoke() }
    BackHandler(enabled = menuPkg != null) { if (rowList) showRows(false) else closeMenu() }

    val hints = when {
        menuPkg != null && rowList -> {
            val (row, ticked) = ticks.firstOrNull { it.first.id == rowLine } ?: ticks.firstOrNull() ?: (null to false)
            hintsFor(HintSpot.ManageMenu(row?.let { (if (ticked) "Remove from " else "Add to ") + it.name }.orEmpty(), back = "Menu"))
        }
        menuPkg != null -> hintsFor(HintSpot.ManageMenu((menuItem ?: menuItems.firstOrNull())?.label.orEmpty()))
        onPill -> hintsFor(HintSpot.Pill(back))
        else -> hintsFor(HintSpot.ManageTile(back))
    }
    ScreenFrame(heading = state.title(), hints = hints, barBack = back) {
        SegmentedPills(
            pills,
            selected = filters.indexOf(filter).coerceAtLeast(0),
            onSelect = { filter = filters[it] },
            onFocused = { onPill = true },
            trackPx = 60, padPx = 6, pillPadPx = 22, gapPx = 6,
            selectedFocus = pillFocus,
        )
        // Rows are keyed by where they are, not by their first app: an app leaving must not rebuild its whole row.
        WindowColumn(rows, { row, _ -> row }, window, ROWS_SHOWN, rowPx = TILE_H, gapPx = GAP, modifier = Modifier.padding(top = px(GRID_TOP))) { rowIndex, row, slot ->
            Row(slot, horizontalArrangement = Arrangement.spacedBy(px(GAP))) {
                row.forEachIndexed { column, app ->
                    key(app.packageName) {
                        // Read with [version], which is bumped when a row changes: rows are not observed here.
                        val mark = remember(app.packageName, version, state.forRow) { state.markFor(app.packageName) }
                        ManageTile(
                            app, loader,
                            mark = mark,
                            hidden = state.isHidden(app.packageName),
                            menuOpen = app.packageName == menuPkg,
                            onClick = { openMenu(app) },
                            onFocused = { onPill = false },
                            modifier = Modifier.focusRequester(tileFocus.getOrPut(app.packageName) { FocusRequester() })
                                .focusProperties {
                                    // Up from any tile of the top row goes to the filter that is chosen.
                                    if (rowIndex == 0) up = pillFocus
                                    // A row ends at its last tile.
                                    if (column == 0) left = FocusRequester.Cancel
                                    if (column == row.lastIndex) right = FocusRequester.Cancel
                                },
                        )
                    }
                }
            }
        }
        if (menuApp != null && menuItems.isNotEmpty()) {
            val at = shown.indexOf(menuApp)
            val tileX = at % APP_COLUMNS * (TILE_W + GAP)
            val tileY = GRID_TOP + (at / APP_COLUMNS - window.startFor(rows.size, ROWS_SHOWN)) * (TILE_H + GAP)
            // Beside the tile on the right, or on the left when the right would cross the page's margin.
            val menuW = textRoom(MENU_W, LookState.look.textSize.scale)
            val x = if (tileX + TILE_W + 14 + menuW <= 1720) tileX + TILE_W + 14 else tileX - 14 - menuW
            // 24 above its tile's top, kept on the page and clear of the hint bar: the rule every menu follows.
            val y = menuTop(tileY, menuHeight(if (rowList) ticks.size else menuItems.size))
            if (rowList) MenuPanel(
                // OK adds or takes out and leaves the list open; Back goes back to the menu.
                "Add to a row", ticks, { it.first.id }, menuFirst,
                onItem = { (row, _) -> state.toggle(menuApp.packageName, row.id); version++; onChanged() },
                onFocused = { rowLine = it.first.id },
                modifier = Modifier.offset(px(x), px(y)),
            ) { (row, ticked), focused -> RowTickLine(row, ticked, focused) }
            else MenuPanel(
                menuApp.label, menuItems, { it.label }, menuFirst,
                onItem = { choose(it, menuApp) },
                onFocused = { menuItem = it },
                modifier = Modifier.offset(px(x), px(y)),
            ) { item, focused ->
                Icon(menuIcon(item), AtomixColors.Text, 26)
                BasicText(item.label, style = AtomixType.button().copy(fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * One app in manage apps (232×140): banner top left, its [mark] top right (a tick when it is in the row manage apps is
 * for, a house when it is for no row and the app is in one), its name below.
 */
@Composable
private fun ManageTile(
    app: AppEntry,
    loader: ImageLoader,
    mark: TileMark,
    hidden: Boolean,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(18))
    Column(
        modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, cornerPx = 18f, glow = FocusGlow.Card)
            .size(px(TILE_W), px(TILE_H))
            .background(if (focused || menuOpen) AtomixColors.Raised else AtomixColors.Tile, shape)
            // The one ring is on the menu's focused item; the tile the menu belongs to keeps a quieter outline.
            .then(if (menuOpen) Modifier.border(px(3), AtomixColors.Ring.copy(alpha = 0.60f), shape) else Modifier)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(start = px(18), top = px(16), end = px(18), bottom = px(14)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // A hidden app is dimmed and says so; it still takes focus, so it can be shown again.
        Row(Modifier.fillMaxWidth().alpha(if (hidden) 0.45f else 1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppArt(app, loader, 86, 48, 8)
            when (mark) {
                TileMark.Tick -> Box(Modifier.size(px(34)).background(AtomixColors.Ice, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(LineIcon.Tick, AtomixColors.Ink, 20)
                }
                TileMark.House -> Icon(LineIcon.House, AtomixColors.Ice, 26)
                TileMark.None -> {}
            }
        }
        // A long name takes two lines, as on the board.
        if (hidden) Column {
            BasicText(app.label, Modifier.alpha(0.45f), style = AtomixType.hint().copy(lineHeight = pxSp(30)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("Hidden", style = AtomixType.description().copy(color = AtomixColors.TextSecondary), maxLines = 1)
        } else BasicText(app.label, style = AtomixType.hint().copy(lineHeight = pxSp(30)), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A short menu beside a tile: [title], then one line per item, drawn by [line]. Lines are keyed by [key], so a line
 * that changes (a tick that flips) keeps its focus. Focus cannot leave it but by Back.
 */
@Composable
private fun <T> MenuPanel(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    first: FocusRequester,
    onItem: (T) -> Unit,
    onFocused: (T) -> Unit,
    modifier: Modifier = Modifier,
    line: @Composable RowScope.(T, Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(px(22))
    Column(
        modifier.width(px(textRoom(MENU_W, LookState.look.textSize.scale))).background(AtomixColors.Panel, shape).border(px(2), AtomixColors.Ice.copy(alpha = 0.24f), shape).padding(px(20)),
        verticalArrangement = Arrangement.spacedBy(px(10)),
    ) {
        BasicText(title, style = AtomixType.rowLabel().copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        items.forEachIndexed { i, item ->
            key(key(item)) {
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Row(
                    (if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            if (i == 0) up = FocusRequester.Cancel
                            if (i == items.lastIndex) down = FocusRequester.Cancel
                        }
                        .onFocusChanged { if (it.isFocused) onFocused(item) }
                        .tvFocus(focused, cornerPx = 16f)
                        .fillMaxWidth()
                        .height(px(MENU_ITEM_H))
                        .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, RoundedCornerShape(px(16)))
                        .clickable(interaction, indication = null) { onItem(item) }
                        .padding(horizontal = px(20)),
                    horizontalArrangement = Arrangement.spacedBy(px(16)),
                    verticalAlignment = Alignment.CenterVertically,
                ) { line(item, focused) }
            }
        }
    }
}

/** A line of the "Add to a row…" list: the row's name, and a tick in a disc when the app is in it (an empty ring if not). */
@Composable
private fun RowScope.RowTickLine(row: HomeRowSpec, ticked: Boolean, focused: Boolean) {
    BasicText(
        row.name, Modifier.weight(1f),
        style = AtomixType.button().copy(fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    if (ticked) Box(Modifier.size(px(34)).background(AtomixColors.Ice, CircleShape), contentAlignment = Alignment.Center) {
        Icon(LineIcon.Tick, AtomixColors.Ink, 20)
    } else Box(Modifier.size(px(34)).border(px(2), AtomixColors.Label, CircleShape))
}

/** The icon beside a menu line. */
private fun menuIcon(item: AppMenuItem): LineIcon = when (item) {
    AppMenuItem.Info -> LineIcon.Info
    is AppMenuItem.AddTo, AppMenuItem.AddToARow, AppMenuItem.Show -> LineIcon.Plus
    is AppMenuItem.RemoveFrom, AppMenuItem.Hide -> LineIcon.Minus
}
