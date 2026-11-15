@file:OptIn(ExperimentalComposeUiApi::class)

package dev.atomix.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.atomix.launcher.prefs.nextRowId
import dev.atomix.launcher.prefs.renamed
import dev.atomix.launcher.prefs.withRowAdded
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.menuHeight
import dev.atomix.launcher.ui.kit.menuTop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.atomix.launcher.prefs.HomeRowSpec
import dev.atomix.launcher.prefs.RowKind
import dev.atomix.launcher.prefs.RowSort
import dev.atomix.launcher.prefs.canHide
import dev.atomix.launcher.prefs.deleted
import dev.atomix.launcher.prefs.moved
import dev.atomix.launcher.prefs.withShown
import dev.atomix.launcher.prefs.withSort
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.SwitchTrack
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How much of the pane shows under Settings' heading, above the hint bar (px at 1920). */
private const val VIEW_H = 780

/** A row's question in place of its options: deleting it. */
private const val ASK_KEEP = "Keep"
private const val ASK_DELETE = "Delete"

/** What [RowsPane]'s name list is naming when it is "New row" rather than a row. */
private const val NEW_ROW = "+new"

/**
 * Settings › Rows and apps, as on its board: "ROWS ON HOME", one entry per row in Home's order (its place, name, what
 * it holds and whether it is shown), the focused one with its options under it; then "APPS", whose one entry opens
 * manage apps. Every change is made through [edit], which saves it and shows it on Home at once.
 */
@Composable
fun RowsPane(
    rows: List<HomeRowSpec>,
    /** The line under a row's name ("6 apps", "3 channels"). */
    summary: (HomeRowSpec) -> String,
    hiddenApps: Int,
    first: FocusRequester,
    edit: ((List<HomeRowSpec>) -> List<HomeRowSpec>) -> Unit,
    chooseApps: (String?) -> Unit,
    onHint: (HintSpot) -> Unit,
    /** The entry to put focus on as the pane opens (a row's id, or [ALL_APPS_ENTRY]): back from manage apps. */
    startOn: String? = null,
) {
    val scope = rememberCoroutineScope()
    val entryFocus = remember { HashMap<String, FocusRequester>() }
    val optionFocus = remember { HashMap<Pair<String, String>, FocusRequester>() }
    val allAppsFocus = remember { FocusRequester() }
    fun entry(id: String) = entryFocus.getOrPut(id) { FocusRequester() }
    fun option(id: String, o: String) = optionFocus.getOrPut(id to o) { FocusRequester() }
    // The entry whose options show: the one focus is on, or in.
    var open by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(noteTick) { if (note != null) { delay(3000); note = null } }
    var scroll by remember { mutableIntStateOf(0) }
    val newRowFocus = remember { FocusRequester() }
    // The row being named ([NEW_ROW] for "New row"), while the name list is open.
    var naming by remember { mutableStateOf<String?>(null) }
    // Back from manage apps: focus returns to the entry it was opened from.
    LaunchedEffect(Unit) {
        val on = startOn ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { (if (on == ALL_APPS_ENTRY) allAppsFocus else entry(on)).requestFocus() }
    }

    /** Where focus goes back to when the name list closes: the row's Rename, or "New row". */
    fun namingHome(id: String): FocusRequester = if (id == NEW_ROW) newRowFocus else option(id, RowOption.Rename.name)

    fun startNaming(id: String) {
        naming = id
        onHint(HintSpot.SettingsChoice)
    }

    /** Closes the name list with nothing changed. Focus goes back first: a focused list that goes hands focus elsewhere. */
    fun closeNaming() {
        val id = naming ?: return
        runCatching { namingHome(id).requestFocus() }
        naming = null
    }

    fun named(name: String) {
        val id = naming ?: return
        runCatching { namingHome(id).requestFocus() }
        naming = null
        if (id == NEW_ROW) {
            // The new row goes at the end, and manage apps opens for it; Back from there lands on its entry.
            val newId = rows.nextRowId()
            edit { it.withRowAdded(name) }
            chooseApps(newId)
        } else edit { it.renamed(id, name) }
    }

    fun toggle(spec: HomeRowSpec) {
        if (spec.shown && !rows.canHide(spec.id)) { note = spec.id; noteTick++; return }
        edit { it.withShown(spec.id, !spec.shown) }
    }

    /** After a move, focus stays on the same option of the moved row, or the other move once that one has gone. */
    fun move(spec: HomeRowSpec, o: RowOption) {
        val stay = focusAfterMove(rows, spec.id, o)
        edit { it.moved(spec.id, if (o == RowOption.MoveUp) -1 else 1) }
        scope.launch {
            withFrameNanos { }
            runCatching { option(spec.id, stay.name).requestFocus() }
        }
    }

    fun choose(spec: HomeRowSpec, o: RowOption) = when (o) {
        RowOption.ChooseApps -> chooseApps(spec.id)
        RowOption.Rename -> startNaming(spec.id)
        RowOption.MoveUp, RowOption.MoveDown -> move(spec, o)
        RowOption.Sort -> edit { it.withSort(spec.id, if (spec.sort == RowSort.AtoZ) RowSort.YourOrder else RowSort.AtoZ) }
        RowOption.Delete -> {
            // The options give way to the question, so focus first goes to the entry, which stays: a focused button
            // that goes hands focus out of the card, and leaving the card closes the question.
            runCatching { entry(spec.id).requestFocus() }
            asking = spec.id
            scope.launch { withFrameNanos { }; runCatching { option(spec.id, ASK_KEEP).requestFocus() } }
            Unit
        }
    }

    fun delete(spec: HomeRowSpec) {
        // Focus goes to the entry that takes the row's place first: a focused control that disappears hands focus to
        // the first thing on the page.
        focusAfterDelete(rows, spec.id)?.let { runCatching { entry(it).requestFocus() } }
        asking = null
        edit { it.deleted(spec.id) }
    }

    // Keep or Back closes the question about deleting, with nothing changed. Declared after Settings' own Back, so
    // it is asked first.
    BackHandler(enabled = asking != null && naming == null) {
        asking?.let { runCatching { entry(it).requestFocus() } }
        asking = null
    }

    val openIndex = rows.indexOfFirst { it.id == open }.takeIf { it >= 0 }
    val newRow = showsNewRow(rows)
    // "New row" counts as one more entry, after the rows.
    val tops = entryTops(rows.size + 1, openIndex)
    val unit = with(LocalDensity.current) { px(1).toPx() }

    Box(Modifier.fillMaxWidth().height(px(VIEW_H)).clipToBounds()) {
        Column(Modifier.fillMaxWidth().graphicsLayer { translationY = -scroll * unit }) {
            GroupLabel("ROWS ON HOME")
            Spacer(Modifier.height(px(12)))
            Column(verticalArrangement = Arrangement.spacedBy(px(ENTRY_GAP))) {
                rows.forEachIndexed { i, spec ->
                    key(spec.id) {
                        val options = optionsFor(rows, spec.id)
                        val isOpen = open == spec.id
                        fun follow() {
                            val top = tops[i]
                            scroll = paneScroll(scroll, top - 24, top + ENTRY_H + (if (isOpen || open == null) OPTIONS_H else 0) + 24, VIEW_H)
                        }
                        // As on the board, the entry and its options are one card, ringed while focus is anywhere in it.
                        var inCard by remember { mutableStateOf(false) }
                        Column(
                            Modifier.fillMaxWidth()
                                .onFocusChanged {
                                    inCard = it.hasFocus
                                    if (it.hasFocus) { open = spec.id; follow() }
                                    else {
                                        // While its name list is open the row keeps its options: Rename takes focus
                                        // back when the list closes.
                                        if (open == spec.id && naming != spec.id) open = null
                                        if (asking == spec.id) asking = null
                                    }
                                }
                                .tvFocus(inCard, cornerPx = 18f, glow = FocusGlow.Row)
                                .background(if (inCard) AtomixColors.Raised else AtomixColors.Tile, RoundedCornerShape(px(18)))
                                .padding(start = px(28), end = px(28), bottom = px(if (isOpen) 20 else 0))
                                .focusGroup(),
                        ) {
                            RowEntry(
                                i + 1, spec, summary(spec), lit = inCard,
                                modifier = Modifier.focusRequester(entry(spec.id))
                                    .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                                    .focusProperties {
                                        options.firstOrNull()?.let { down = option(spec.id, if (asking == spec.id) ASK_KEEP else it.name) }
                                    },
                                onClick = { toggle(spec) },
                                onFocused = { onHint(HintSpot.RowsEntry) },
                            )
                            if (note == spec.id) Note(KEEP_ONE_NOTE, AtomixColors.Amber)
                            if (isOpen) {
                                val next = rows.getOrNull(i + 1)?.let { entry(it.id) } ?: if (newRow) newRowFocus else allAppsFocus
                                Row(
                                    Modifier.height(px(56)),
                                    horizontalArrangement = Arrangement.spacedBy(px(12)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (asking == spec.id) {
                                        BasicText(deleteQuestion(spec.name), style = AtomixType.description().copy(color = AtomixColors.Text), maxLines = 1)
                                        Spacer(Modifier.width(px(12)))
                                        listOf(ASK_KEEP, ASK_DELETE).forEach { b -> key(b) {
                                            OptionButton(
                                                b, null, Modifier.focusRequester(option(spec.id, b)).focusProperties { up = entry(spec.id); down = next },
                                                onClick = { if (b == ASK_KEEP) { asking = null; runCatching { entry(spec.id).requestFocus() } } else delete(spec) },
                                                onFocused = { onHint(HintSpot.RowsOption(b, back = "Close")) },
                                            )
                                        } }
                                    } else options.forEach { o -> key(o.name) {
                                        // Keyed, so a button that goes (Move up on the row now first) takes its focus
                                        // with it instead of handing it to whichever button slides into its place.
                                        val label = optionLabel(o, spec)
                                        OptionButton(
                                            label, optionIcon(o),
                                            Modifier.focusRequester(option(spec.id, o.name)).focusProperties { up = entry(spec.id); down = next },
                                            onClick = { choose(spec, o) },
                                            onFocused = { onHint(HintSpot.RowsOption(label)) },
                                        )
                                    } }
                                }
                            }
                        }
                    }
                }
                if (newRow) NewRowEntry(
                    Modifier.focusRequester(newRowFocus),
                    onClick = { startNaming(NEW_ROW) },
                    onFocused = {
                        if (naming == null) open = null
                        onHint(HintSpot.RowsOption("New row"))
                        val top = entryTops(rows.size + 1, null)[rows.size]
                        scroll = paneScroll(scroll, top - 24, top + ENTRY_H + 24, VIEW_H)
                    },
                )
            }
            Spacer(Modifier.height(px(24)))
            GroupLabel("APPS")
            Spacer(Modifier.height(px(12)))
            SettingRowLike(
                "All apps", hiddenSummary(hiddenApps),
                Modifier.focusRequester(allAppsFocus).onFocusChanged {
                    if (it.isFocused) {
                        open = null
                        onHint(HintSpot.SettingsRow("Open"))
                        // Under the list: 24, the group's label (36), then the 92-high entry.
                        val end = entryTops(rows.size + 1, null)[rows.size] + (if (newRow) ENTRY_H else -ENTRY_GAP)
                        scroll = paneScroll(scroll, end, end + 24 + 36 + ENTRY_H + 24, VIEW_H)
                    }
                },
                onClick = { chooseApps(null) },
            )
        }
        // The name list, over the right end of the row it names, as Settings' other lists are.
        naming?.let { id ->
            val spec = rows.firstOrNull { it.id == id }
            val anchor = (if (id == NEW_ROW) tops[rows.size] else tops[rows.indexOf(spec).coerceAtLeast(0)]) - scroll
            NameChooser(
                title = if (spec == null) "Name the new row" else "Rename",
                current = spec?.name,
                onName = ::named,
                onClose = ::closeNaming,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = px(-20), y = px(menuTop(anchor, menuHeight(PRESET_NAMES.size + 1), VIEW_H - 16))),
            )
        }
    }
}

/** "New row", as the board's: a dashed outline, a plus in a pale disc, its words in the accent. */
@Composable
private fun NewRowEntry(modifier: Modifier, onClick: () -> Unit, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dash = AtomixColors.Ice.copy(alpha = 0.40f)
    Row(
        modifier.onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, cornerPx = 18f, glow = FocusGlow.Row)
            .fillMaxWidth().height(px(ENTRY_H))
            .background(if (focused) AtomixColors.Raised else AtomixColors.Inset, RoundedCornerShape(px(18)))
            .drawBehind {
                if (!focused) {
                    // One board pixel, from the entry's height.
                    val u = size.height / ENTRY_H
                    drawRoundRect(
                        dash, topLeft = Offset(u, u), size = Size(size.width - 2 * u, size.height - 2 * u),
                        cornerRadius = CornerRadius(17 * u),
                        style = Stroke(width = 2 * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * u, 7 * u))),
                    )
                }
            }
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = px(28)),
        horizontalArrangement = Arrangement.spacedBy(px(22)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(px(44)).background(AtomixColors.Ice.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(LineIcon.Plus, AtomixColors.Ice, 26)
        }
        BasicText("New row", style = AtomixType.rowLabel().copy(fontWeight = FontWeight.SemiBold, color = if (focused) AtomixColors.Text else AtomixColors.Ice), maxLines = 1)
    }
}

/** One row's entry: its place on Home in a round badge, its name and what it holds, and a "Shown" or "Hidden" switch. */
@Composable
private fun RowEntry(place: Int, spec: HomeRowSpec, summary: String, lit: Boolean, modifier: Modifier, onClick: () -> Unit, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused = lit
    val name = spec.name.ifEmpty { if (spec.kind == RowKind.OnNow) "On now" else "Inputs" }
    Row(
        modifier.onFocusChanged { if (it.isFocused) onFocused() }
            .fillMaxWidth().height(px(ENTRY_H))
            .clickable(interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(px(22)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(px(44)).background(if (focused) AtomixColors.Ice else AtomixColors.Inset, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "$place",
                style = AtomixType.hint().copy(fontWeight = FontWeight.Bold, color = if (focused) AtomixColors.Ink else if (spec.shown) AtomixColors.Text else AtomixColors.Label),
            )
        }
        Column(Modifier.weight(1f)) {
            BasicText(name, style = AtomixType.rowLabel().copy(color = if (spec.shown) AtomixColors.Text else AtomixColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(summary, style = AtomixType.description(), maxLines = 1)
        }
        BasicText(if (spec.shown) "Shown" else "Hidden", style = AtomixType.hint().copy(fontWeight = FontWeight.SemiBold, color = if (spec.shown) AtomixColors.Text else AtomixColors.TextSecondary))
        SwitchTrack(spec.shown)
    }
}

/** One of a row's options: 56 high, its icon and words; ice with dark words when focused, as on the board. */
@Composable
private fun OptionButton(label: String, icon: dev.atomix.launcher.ui.kit.LineIcon?, modifier: Modifier, onClick: () -> Unit, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ink = if (focused) AtomixColors.Ink else AtomixColors.Text
    // Also when its words change under focus (Sort), so the hint says what OK does now.
    LaunchedEffect(focused, label) { if (focused) onFocused() }
    Row(
        modifier
            .height(px(56))
            .background(if (focused) AtomixColors.Ice else AtomixColors.Inset, RoundedCornerShape(px(14)))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = px(22)),
        horizontalArrangement = Arrangement.spacedBy(px(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, ink, 24)
        BasicText(label, style = AtomixType.hint().copy(fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold, color = ink), maxLines = 1)
    }
}

/** The "All apps" entry: a plain row with what is hidden under its name and a chevron, as the board's. */
@Composable
private fun SettingRowLike(label: String, description: String, modifier: Modifier, onClick: () -> Unit) =
    dev.atomix.launcher.ui.kit.SettingRow(label, onClick, modifier, description = description, ending = dev.atomix.launcher.ui.kit.RowEnding.Value("", chevron = true))
