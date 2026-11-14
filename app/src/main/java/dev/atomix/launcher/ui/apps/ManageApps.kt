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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.ui.kit.AppArt
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.MENU_W
import dev.atomix.launcher.ui.kit.menuHeight
import dev.atomix.launcher.ui.kit.menuTop
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
 * Manage apps, as far as it goes for now: which apps are in Home's "Your apps" row. OK on an app opens its menu (add
 * to the row or remove from it, and the TV's own App info page); Back closes the menu. [onChanged] is told when the
 * row changes.
 */
@Composable
fun ManageAppsScreen(state: AppsTabState, loader: ImageLoader, onInfo: (AppEntry) -> Unit, onChanged: () -> Unit) {
    val apps by state.apps.collectAsState()
    // Bumped when the row changes: what is in the row is not observable, so the ticks and counts are re-read on it.
    var version by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(AppFilter.All) }
    val shown = remember(apps, filter, version) { state.shown(filter) }
    val rows = remember(shown) { shown.chunked(APP_COLUMNS) }
    val pills = remember(apps, version) { AppFilter.entries.map(state::pillLabel) }
    val window = remember(filter) { WindowState() }
    val tileFocus = remember { HashMap<String, FocusRequester>() }
    val pillFocus = remember { FocusRequester() }
    val menuFirst = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var onPill by remember { mutableStateOf(false) }
    var menuPkg by remember { mutableStateOf<String?>(null) }
    var menuItem by remember { mutableStateOf<AppMenuItem?>(null) }
    val menuApp = shown.firstOrNull { it.packageName == menuPkg }
    val menuItems = remember(menuPkg, apps, version) { menuPkg?.let(state::menuFor).orEmpty() }

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
    }

    fun openMenu(app: AppEntry) {
        if (state.menuFor(app.packageName).isEmpty()) return
        menuPkg = app.packageName
        scope.launch { withFrameNanos { }; runCatching { menuFirst.requestFocus() } }
    }

    fun choose(item: AppMenuItem, app: AppEntry) {
        when (item) {
            AppMenuItem.Info -> onInfo(app)
            AppMenuItem.Add, AppMenuItem.Remove -> {
                // Under "In this row" a removed app's tile goes away, so focus goes first to a neighbour that stays put.
                val leaving = item == AppMenuItem.Remove && filter == AppFilter.InRow
                closeMenu(if (leaving) focusAfterRemove(shown.map { it.packageName }, shown.indexOf(app), APP_COLUMNS) else app.packageName)
                state.toggle(app.packageName)
                version++
                onChanged()
            }
        }
    }

    // An app that is uninstalled while its menu is open takes the menu with it.
    LaunchedEffect(menuPkg, menuItems) {
        if (menuPkg != null && menuItems.isEmpty()) closeMenu(shown.firstOrNull()?.packageName)
    }
    BackHandler(enabled = menuPkg != null) { closeMenu() }

    val hints = when {
        menuPkg != null -> hintsFor(HintSpot.ManageMenu((menuItem ?: menuItems.firstOrNull())?.label.orEmpty()))
        onPill -> hintsFor(HintSpot.Pill("Apps"))
        else -> hintsFor(HintSpot.ManageTile)
    }
    ScreenFrame(heading = "Choose apps for “Your apps”", hints = hints, barBack = "Apps") {
        SegmentedPills(
            pills,
            selected = filter.ordinal,
            onSelect = { filter = AppFilter.entries[it] },
            onFocused = { onPill = true },
            trackPx = 60, padPx = 6, pillPadPx = 22, gapPx = 6,
            selectedFocus = pillFocus,
        )
        // Rows are keyed by where they are, not by their first app: an app leaving must not rebuild its whole row.
        WindowColumn(rows, { row, _ -> row }, window, ROWS_SHOWN, rowPx = TILE_H, gapPx = GAP, modifier = Modifier.padding(top = px(GRID_TOP))) { rowIndex, row, slot ->
            Row(slot, horizontalArrangement = Arrangement.spacedBy(px(GAP))) {
                row.forEachIndexed { column, app ->
                    key(app.packageName) {
                        ManageTile(
                            app, loader,
                            inRow = state.isFavourite(app.packageName),
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
            val x = if (tileX + TILE_W + 14 + MENU_W <= 1720) tileX + TILE_W + 14 else tileX - 14 - MENU_W
            // 24 above its tile's top, kept on the page and clear of the hint bar: the rule every menu follows.
            val y = menuTop(tileY, menuHeight(menuItems.size))
            AppMenu(
                menuApp.label, menuItems, menuFirst,
                onItem = { choose(it, menuApp) },
                onFocused = { menuItem = it },
                modifier = Modifier.offset(px(x), px(y)),
            )
        }
    }
}

/** One app in manage apps (232×140): banner top left, a tick top right when it is in the row, its name below. */
@Composable
private fun ManageTile(
    app: AppEntry,
    loader: ImageLoader,
    inRow: Boolean,
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppArt(app, loader, 86, 48, 8)
            if (inRow) Box(Modifier.size(px(34)).background(AtomixColors.Ice, CircleShape), contentAlignment = Alignment.Center) {
                Icon(LineIcon.Tick, AtomixColors.Ink, 20)
            }
        }
        // A long name takes two lines, as on the board.
        BasicText(app.label, style = AtomixType.hint().copy(lineHeight = pxSp(30)), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** An app's menu: its name, then one row per thing that can be done with it. Focus cannot leave it but by Back. */
@Composable
private fun AppMenu(
    title: String,
    items: List<AppMenuItem>,
    first: FocusRequester,
    onItem: (AppMenuItem) -> Unit,
    onFocused: (AppMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(px(22))
    Column(
        modifier.width(px(MENU_W)).background(AtomixColors.Panel, shape).border(px(2), AtomixColors.Ice.copy(alpha = 0.24f), shape).padding(px(20)),
        verticalArrangement = Arrangement.spacedBy(px(10)),
    ) {
        BasicText(title, style = AtomixType.rowLabel().copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        items.forEachIndexed { i, item ->
            val interaction = remember(item) { MutableInteractionSource() }
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
            ) {
                Icon(if (item == AppMenuItem.Info) LineIcon.Info else LineIcon.Plus, AtomixColors.Text, 26)
                BasicText(item.label, style = AtomixType.button().copy(fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1)
            }
        }
    }
}
