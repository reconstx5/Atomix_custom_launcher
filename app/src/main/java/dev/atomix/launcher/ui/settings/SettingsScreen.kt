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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import dev.atomix.launcher.ui.kit.ChipButton
import dev.atomix.launcher.ui.kit.GlassPanel
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import kotlinx.coroutines.launch

enum class SettingsSection(val title: String) {
    LiveTv("Live TV"), Server("Server"), Profile("Profile"), Display("Display"), HomeButton("Home button"), About("About")
}

/** Facts Settings shows that live outside [SettingsState]. [server] is read on every draw (Find again may change it). */
class SettingsInfo(val server: () -> String?, val profile: String?, val version: String, val homeOverrideOn: Boolean)

class SettingsActions(
    val testChannel: (String) -> Unit,
    val manageSources: () -> Unit,
    val switchProfile: () -> Unit,
    val setReduced: (Boolean) -> Unit,
    val close: () -> Unit,
)

/**
 * Settings: sections on the left (focusing one shows it), its detail on the right. OK on a section moves into the
 * detail, Left from the detail returns to that section, Back closes Settings.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(state: SettingsState, info: SettingsInfo, reduced: Boolean, nowMs: Long, actions: SettingsActions) {
    val live by state.live.collectAsState()
    var section by remember { mutableStateOf(SettingsSection.LiveTv) }
    val requesters = remember { SettingsSection.entries.associateWith { FocusRequester() } }
    val detailFirst = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var serverNote by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = actions.close)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requesters.getValue(section).requestFocus() }
        state.load()
    }
    val first = Modifier.focusRequester(detailFirst)

    Box(Modifier.fillMaxSize().background(AtomixColors.DeepSpace)) {
        BasicText("Settings", Modifier.padding(start = px(116), top = px(64)), style = AtomixType.headline())
        Column(Modifier.padding(start = px(116), top = px(190)).width(px(420)), verticalArrangement = Arrangement.spacedBy(px(12))) {
            SettingsSection.entries.forEach { s ->
                SectionItem(
                    s.title,
                    selected = s == section,
                    modifier = Modifier.focusRequester(requesters.getValue(s)),
                    onFocus = { section = s },
                    onOk = { runCatching { detailFirst.requestFocus() } },
                )
            }
        }
        GlassPanel(null, Modifier.padding(start = px(600), top = px(190), end = px(116), bottom = px(80)).fillMaxSize(), RoundedCornerShape(px(24))) {
            // Focus stays in the detail: Left goes back to the section it belongs to (not the nearest one), and the
            // other directions stop at the panel's edge instead of jumping to another section.
            Column(
                Modifier
                    .focusProperties { exit = { dir -> if (dir == FocusDirection.Left) requesters.getValue(section) else FocusRequester.Cancel } }
                    .focusGroup()
                    .padding(px(48)),
                verticalArrangement = Arrangement.spacedBy(px(20)),
            ) {
                BasicText(section.title, style = AtomixType.rowHeader())
                when (section) {
                    SettingsSection.LiveTv -> {
                        when {
                            live.loading && live.sources.isEmpty() -> Body("Checking…")
                            !live.enabled -> Body("Live TV isn't set up on your TV server.")
                        }
                        live.sources.forEach { src ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(24))) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(px(4))) {
                                    BasicText(src.name, style = AtomixType.title())
                                    val refreshed = ago(src.lastRefresh ?: src.lastOk, nowMs)?.let { "refreshed $it" }
                                    Body(listOfNotNull(src.summary ?: "${src.channels} channels", refreshed).joinToString(" · "))
                                    src.error?.let { Body(it, AtomixColors.Coral) }
                                    src.warning?.let { Body(it) }
                                }
                                if (live.admin) ChipButton(
                                    if (busy == src.id) "Refreshing…" else "Refresh",
                                    {
                                        if (busy == null) scope.launch {
                                            busy = src.id
                                            try { state.refreshSource(src.id) } finally { busy = null }
                                        }
                                    },
                                )
                            }
                        }
                        live.error?.let { Body(it, AtomixColors.Coral) }
                        Row(Modifier.padding(top = px(12)), horizontalArrangement = Arrangement.spacedBy(px(20))) {
                            ChipButton("Test a channel", { state.testChannelId()?.let(actions.testChannel) }, first, icon = LineIcon.Play)
                            if (live.admin) ChipButton("Manage sources in Atomix", actions.manageSources)
                        }
                        if (!live.admin && !live.loading) Body("Ask an admin to manage Live TV sources.")
                    }
                    SettingsSection.Server -> {
                        Body("Address: ${info.server() ?: "not set"}")
                        Body(
                            "Status: " + when {
                                live.loading || busy == SERVER -> "checking…"
                                live.error == null -> "connected"
                                else -> "can't reach the server"
                            },
                        )
                        ChipButton(
                            if (busy == SERVER) "Looking…" else "Find again",
                            {
                                if (busy == null) scope.launch {
                                    busy = SERVER
                                    try {
                                        serverNote = if (state.findServerAgain()) "Found the server at ${info.server()}." else "Couldn't find an Atomix server on this network."
                                        state.load()
                                    } finally { busy = null }
                                }
                            },
                            first,
                        )
                        serverNote?.let { Body(it) }
                        Body("Use this after the server's address changes.")
                    }
                    SettingsSection.Profile -> {
                        Body("Watching as ${info.profile ?: "unknown"}")
                        ChipButton("Switch profile", actions.switchProfile, first)
                    }
                    SettingsSection.Display -> {
                        Body("Reduced effects: solid panels and no animation, for a steadier picture on this TV. The focus ring stays.")
                        ChipButton(if (reduced) "Reduced effects: On" else "Reduced effects: Off", { actions.setReduced(!reduced) }, first)
                    }
                    SettingsSection.HomeButton -> {
                        Body(
                            if (info.homeOverrideOn) "On: pressing Home opens Atomix TV."
                            else "Off: pressing Home opens the Google TV home screen.",
                            AtomixColors.TextPrimary,
                        )
                        Body("To change it: TV Settings › Device Preferences › Accessibility › Atomix Home button.")
                    }
                    SettingsSection.About -> {
                        Body("Atomix TV ${info.version}", AtomixColors.TextPrimary)
                        Body("Live TV and your apps, from your Atomix server.")
                    }
                }
            }
        }
    }
}

private const val SERVER = "server"

@Composable
private fun Body(text: String, color: Color = AtomixColors.TextSecondary) {
    BasicText(text, style = AtomixType.body().copy(color = color))
}

@Composable
private fun SectionItem(title: String, selected: Boolean, modifier: Modifier, onFocus: () -> Unit, onOk: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    val shape = RoundedCornerShape(px(16))
    Box(
        modifier
            .fillMaxWidth()
            .height(px(72))
            .tvFocus(focused, cornerPx = 16f)
            .background(
                when {
                    focused -> Color.White
                    selected -> AtomixColors.GlassClear
                    else -> Color.Transparent
                },
                shape,
            )
            .clickable(interaction, indication = null, onClick = onOk)
            .padding(horizontal = px(28)),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(title, style = AtomixType.menu().copy(color = if (focused) AtomixColors.DeepSpace else AtomixColors.TextPrimary))
    }
}
