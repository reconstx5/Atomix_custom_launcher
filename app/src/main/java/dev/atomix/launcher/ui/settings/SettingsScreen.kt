@file:OptIn(ExperimentalComposeUiApi::class) // focus exit rules, FocusRequester.Cancel

package dev.atomix.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.atomix.launcher.shell.LocalTopBarFocus
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.RowEnding
import dev.atomix.launcher.ui.kit.ScreenFrame
import dev.atomix.launcher.ui.kit.SettingRow
import dev.atomix.launcher.ui.kit.Tone
import dev.atomix.launcher.ui.kit.hintsFor
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import kotlinx.coroutines.launch

/**
 * Facts Settings shows that live outside [SettingsState]. [server] is read on every draw (finding the server again
 * may change it). [homeOverrideOn] is re-read each time the launcher comes back to the front.
 */
class SettingsInfo(val server: () -> String?, val profile: String?, val version: String, val homeOverrideOn: Boolean)

class SettingsActions(
    val testChannel: (String) -> Unit,
    val manageSources: () -> Unit,
    val switchProfile: () -> Unit,
    val setReduced: (Boolean) -> Unit,
    /** Opens the TV's own settings, where the Home-button service is switched (under Accessibility). */
    val openAccessibility: () -> Unit,
)

private const val SERVER = "server"

/** What OK does on each row, for the hint bar; null for a row that only states something. */
private fun actionFor(item: SettingsItem?, reduced: Boolean): String? = when (item) {
    SettingsItem.HomeButton -> "Open TV settings"
    SettingsItem.ReducedEffects -> if (reduced) "Turn off" else "Turn on"
    SettingsItem.LiveSources -> "Refresh"
    SettingsItem.TestChannel -> "Play"
    SettingsItem.ManageSources -> "Open Atomix"
    SettingsItem.FindServer -> "Search"
    SettingsItem.SwitchProfile -> "Switch"
    SettingsItem.Server, SettingsItem.Version, null -> null
}

private fun iconFor(section: SettingsSection): LineIcon = when (section) {
    SettingsSection.Setup -> LineIcon.House
    SettingsSection.Appearance -> LineIcon.Drop
    SettingsSection.HomeLayout -> LineIcon.Apps
    SettingsSection.Rows -> LineIcon.List
    SettingsSection.Wallpaper -> LineIcon.Library
    SettingsSection.Inputs -> LineIcon.Inputs
    SettingsSection.LiveTv -> LineIcon.Tuner
    SettingsSection.Account -> LineIcon.Person
    SettingsSection.About -> LineIcon.Info
}

/**
 * Settings: the sections that have something working on the left (focusing one shows it), its rows on the right.
 * OK or Right on a section moves into its rows; Left from the rows returns to that section; Back steps out one
 * level: rows, then the section list, then Home.
 */
@Composable
fun SettingsScreen(state: SettingsState, info: SettingsInfo, reduced: Boolean, nowMs: Long, actions: SettingsActions) {
    val live by state.live.collectAsState()
    val sections = remember { listedSections() }
    var section by remember { mutableStateOf(sections.first()) }
    val sectionFocus = remember { sections.associateWith { FocusRequester() } }
    val rowsFirst = remember { FocusRequester() }
    val bar = LocalTopBarFocus.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var serverNote by remember { mutableStateOf<String?>(null) }
    var inRows by remember { mutableStateOf(false) }
    var focusedItem by remember { mutableStateOf<SettingsItem?>(null) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { sectionFocus.getValue(section).requestFocus() }
        state.load()
    }
    // Back in the rows steps out to the section list; in the list it is the shell's, and goes Home.
    BackHandler(enabled = inRows) { runCatching { sectionFocus.getValue(section).requestFocus() } }
    // Down from the top bar returns to the section that is open.
    val barDown by rememberUpdatedState { sectionFocus.getValue(section) }
    DisposableEffect(bar) {
        bar?.down = { barDown() }
        onDispose { bar?.down = null }
    }

    val hints = when {
        inRows -> hintsFor(HintSpot.SettingsRow(actionFor(focusedItem, reduced)))
        // A section with nothing to move into offers no "OK Open".
        else -> hintsFor(HintSpot.SettingsSections).filter { hasRowToOpen(section) || it.key != "OK" }
    }
    ScreenFrame(heading = "Settings", hints = hints) {
        Column(Modifier.width(px(430)), verticalArrangement = Arrangement.spacedBy(px(8))) {
            sections.forEachIndexed { i, s ->
                SectionItem(
                    s,
                    open = s == section,
                    modifier = Modifier.focusRequester(sectionFocus.getValue(s)).focusProperties { if (i == 0 && bar != null) up = bar.marked },
                    onFocus = { section = s },
                    onOpen = { runCatching { rowsFirst.requestFocus() } },
                )
            }
        }
        // Focus stays in the rows: Left goes back to the section they belong to (not the nearest one), and the other
        // arrows stop at the edge instead of finding another section. Focus sent elsewhere by the app passes.
        Column(
            Modifier.padding(start = px(470)).fillMaxWidth()
                .onFocusChanged { inRows = it.hasFocus }
                .focusProperties {
                    exit = { direction ->
                        when (direction) {
                            FocusDirection.Left -> sectionFocus.getValue(section)
                            FocusDirection.Up, FocusDirection.Down, FocusDirection.Right -> FocusRequester.Cancel
                            else -> FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(px(12)),
        ) {
            // The first row that can take focus is where OK or Right on the section lands.
            var firstTaken = false
            fun first(focusable: Boolean): Modifier = if (focusable && !firstTaken) { firstTaken = true; Modifier.focusRequester(rowsFirst) } else Modifier

            rowsIn(section).forEach { item ->
                when (item) {
                    SettingsItem.HomeButton -> {
                        GroupLabel("HOME SCREEN")
                        SettingRow(
                            "Home button opens Atomix TV", actions.openAccessibility, first(true),
                            // OK can only open the TV's settings at their first page, so the row says where to go.
                            description = "Change it in TV settings: Device Preferences \u203A Accessibility",
                            ending = homeButtonEnding(info.homeOverrideOn),
                            onFocused = { focusedItem = item },
                        )
                    }
                    SettingsItem.ReducedEffects -> {
                        GroupLabel("FOCUS AND MOTION")
                        SettingRow(
                            "Reduced effects", { actions.setReduced(!reduced) }, first(true),
                            description = "No glow or animation, for a steadier picture",
                            ending = RowEnding.Switch(reduced),
                            onFocused = { focusedItem = item },
                        )
                    }
                    SettingsItem.LiveSources -> {
                        GroupLabel("SOURCES")
                        live.sources.forEach { src ->
                            key(src.id) {
                                val refreshed = ago(src.lastRefresh ?: src.lastOk, nowMs)?.let { "refreshed $it" }
                                SettingRow(
                                    src.name,
                                    onClick = if (!live.admin) null else ({
                                        if (busy == null) scope.launch {
                                            busy = src.id
                                            try { state.refreshSource(src.id) } finally { busy = null }
                                        }
                                    }),
                                    modifier = first(live.admin),
                                    description = src.error ?: src.warning
                                        ?: listOfNotNull(src.summary ?: "${src.channels} channels", refreshed).joinToString(" · "),
                                    descriptionColor = if (src.error != null) AtomixColors.Amber else Color.Unspecified,
                                    ending = if (live.admin) RowEnding.Value(if (busy == src.id) "Refreshing…" else "Refresh", chevron = false) else RowEnding.None,
                                    onFocused = { focusedItem = item },
                                )
                            }
                        }
                    }
                    SettingsItem.TestChannel -> SettingRow(
                        "Test a channel", { state.testChannelId()?.let(actions.testChannel) }, first(true),
                        ending = RowEnding.Value("", chevron = true),
                        onFocused = { focusedItem = item },
                    )
                    SettingsItem.ManageSources -> {
                        if (live.admin) SettingRow(
                            "Manage sources in Atomix", actions.manageSources, first(true),
                            ending = RowEnding.Value("", chevron = true),
                            onFocused = { focusedItem = item },
                        )
                        // What today's Settings says about Live TV, as one line under the rows.
                        when {
                            live.error != null -> Note(live.error!!, AtomixColors.Amber)
                            live.loading && live.sources.isEmpty() -> Note("Checking…")
                            !live.enabled -> Note("Live TV isn't set up on your TV server.")
                            !live.admin && !live.loading -> Note("Ask an admin to manage Live TV sources.")
                        }
                    }
                    SettingsItem.Server -> {
                        GroupLabel("SERVER")
                        SettingRow(
                            "Server", null,
                            description = info.server() ?: "Not set",
                            ending = when {
                                live.loading || busy == SERVER -> RowEnding.Status("Checking…", Tone.Plain)
                                live.error == null -> RowEnding.Status("Connected", Tone.Good)
                                else -> RowEnding.Status("Can't reach the server", Tone.Warn)
                            },
                        )
                    }
                    SettingsItem.FindServer -> {
                        SettingRow(
                            "Find the server again",
                            {
                                if (busy == null) scope.launch {
                                    busy = SERVER
                                    try {
                                        serverNote = if (state.findServerAgain()) "Found the server at ${info.server()}." else "Couldn't find an Atomix server on this network."
                                        state.load()
                                    } finally { busy = null }
                                }
                            },
                            first(true),
                            description = "Use this after the server's address changes.",
                            ending = if (busy == SERVER) RowEnding.Value("Looking…", chevron = false) else RowEnding.None,
                            onFocused = { focusedItem = item },
                        )
                        serverNote?.let { Note(it) }
                    }
                    SettingsItem.SwitchProfile -> {
                        Spacer(Modifier.height(px(24)))
                        GroupLabel("PROFILE")
                        SettingRow(
                            "Switch profile", actions.switchProfile, first(true),
                            description = "Watching as ${info.profile ?: "unknown"}",
                            ending = RowEnding.Value("", chevron = true),
                            onFocused = { focusedItem = item },
                        )
                    }
                    SettingsItem.Version -> SettingRow(
                        "Atomix TV", null,
                        description = "Live TV and your apps, from your Atomix server.",
                        ending = RowEnding.Value(info.version, chevron = false),
                    )
                }
            }
        }
    }
}

/** A group's label over its rows, in its 36-high line. */
@Composable
private fun GroupLabel(text: String) {
    // The rows are 12 apart; the label's line already holds the space between it and its first row.
    Box(Modifier.height(px(36 - 12))) { BasicText(text, style = AtomixType.groupLabel(), maxLines = 1) }
}

/** One line of plain words under a group's rows. */
@Composable
private fun Note(text: String, color: Color = AtomixColors.TextSecondary) {
    BasicText(text, Modifier.padding(start = px(28), top = px(4)), style = AtomixType.description().copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** One section in the list (76 high): the open one sits on a tile in bold; OK or Right moves into its rows. */
@Composable
private fun SectionItem(section: SettingsSection, open: Boolean, modifier: Modifier, onFocus: () -> Unit, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onPreviewKeyEvent {
                if (it.key != Key.DirectionRight) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyDown) onOpen()
                true
            }
            .tvFocus(focused, cornerPx = 18f)
            .fillMaxWidth()
            .height(px(76))
            .background(
                when {
                    focused -> AtomixColors.Raised
                    open -> AtomixColors.Tile
                    else -> Color.Transparent
                },
                RoundedCornerShape(px(18)),
            )
            .clickable(interaction, indication = null, onClick = onOpen)
            .padding(horizontal = px(22)),
        horizontalArrangement = Arrangement.spacedBy(px(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconFor(section), if (open) AtomixColors.Ice else AtomixColors.Label, 30)
        BasicText(
            section.title,
            style = AtomixType.rowLabel().copy(
                fontWeight = if (open) FontWeight.Bold else FontWeight.Medium,
                color = if (open || focused) AtomixColors.Text else AtomixColors.TextSecondary,
            ),
            maxLines = 1,
        )
    }
}
