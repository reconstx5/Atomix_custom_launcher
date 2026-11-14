@file:OptIn(ExperimentalComposeUiApi::class) // FocusRequester.Cancel

package dev.atomix.launcher.ui.livetv

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.livetv.source.onAir
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.shell.HomeUi
import dev.atomix.launcher.shell.LocalTopBarFocus
import dev.atomix.launcher.shell.restoresFocus
import dev.atomix.launcher.ui.home.Artwork
import dev.atomix.launcher.ui.home.NameChip
import dev.atomix.launcher.ui.home.ProgressBar
import dev.atomix.launcher.ui.home.artScrim
import dev.atomix.launcher.ui.home.onAirLines
import dev.atomix.launcher.ui.home.onAirTitle
import dev.atomix.launcher.ui.kit.EmptyState
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.LogoPlate
import dev.atomix.launcher.ui.kit.RowEnding
import dev.atomix.launcher.ui.kit.ScreenFrame
import dev.atomix.launcher.ui.kit.SettingRow
import dev.atomix.launcher.ui.kit.WindowColumn
import dev.atomix.launcher.ui.kit.WindowState
import dev.atomix.launcher.ui.kit.hintsFor
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp
import kotlinx.coroutines.delay

const val ROW_CHANNELS = "channels"

/** How many channel rows show at once. */
private const val ROWS_SHOWN = 7

/** How long focus rests on a row before its artwork is asked for, so holding Down does not load a picture per row. */
private const val ART_SETTLE_MS = 250L

/**
 * Every TV channel in number order on the left; on the right a preview of the focused channel and, under it, the one
 * "Show on Home" switch. OK on a row plays it (zapping through every channel); Right reaches the switch and Left
 * returns to the same row. Focus comes back to the remembered row on every [restoreTick].
 */
@Composable
fun LiveTvTab(
    state: LiveTvTabState,
    ui: HomeUi,
    loader: ImageLoader,
    url: (String?) -> String?,
    is24: Boolean,
    memory: FocusMemory,
    restoreTick: Int,
    onPlay: (Channel) -> Unit,
) {
    val ready = ui.live as? LiveState.Ready
    val all = ready?.all
    val onHome = remember(ready) { ready?.pinned?.map { it.id }?.toSet().orEmpty() }
    val window = remember { WindowState() }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    val switchFocus = remember { FocusRequester() }
    val bar = LocalTopBarFocus.current
    var focusedId by remember { mutableStateOf(memory.current()?.key) }
    var onSwitch by remember { mutableStateOf(false) }

    val restoredAt = remember { intArrayOf(Int.MIN_VALUE) }
    LaunchedEffect(restoreTick, all?.size) {
        val ids = all?.map { it.id } ?: return@LaunchedEffect
        // Coming to the tab always restores. A channel list that only changed does not take focus from the top bar.
        if (!restoresFocus(byTick = restoredAt[0] != restoreTick, barHasFocus = bar?.hasFocus == true)) return@LaunchedEffect
        val target = memory.resolve(mapOf(ROW_CHANNELS to ids)) ?: return@LaunchedEffect
        window.show(target.index, ids.size, ROWS_SHOWN)
        withFrameNanos { }
        runCatching { rowFocus[target.key]?.requestFocus() }
        restoredAt[0] = restoreTick
    }

    /**
     * The focused channel's row as a focus target, or null when it is not one of the rows showing (the list changed
     * under it): a row that is not built must never be named as a target.
     */
    fun focusedRow(): FocusRequester? {
        val list = all ?: return null
        return if (window.shows(list.indexOfFirst { it.id == focusedId }, list.size, ROWS_SHOWN)) rowFocus[focusedId] else null
    }

    // Down from the top bar returns to the row focus was on.
    val barDown by rememberUpdatedState { focusedRow() ?: FocusRequester.Default }
    DisposableEffect(bar) {
        bar?.down = { barDown() }
        onDispose { bar?.down = null }
    }

    // The preview's words follow focus at once; its picture waits for focus to rest, and the old one goes at once.
    var artFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedId) {
        artFor = null
        delay(ART_SETTLE_MS)
        artFor = focusedId
    }

    val previewed = all?.firstOrNull { it.id == focusedId }
    // Asked of the server's list as it is now: a channel that has gone has no switch, so nothing acts on a stale id.
    val switchFor = if (all == null) null else state.switchTarget(focusedId)
    val hints = when {
        onSwitch && switchFor != null -> hintsFor(HintSpot.LiveSwitch(on = switchFor.id in onHome))
        previewed != null -> hintsFor(HintSpot.LiveRow(previewed.name))
        else -> emptyList()
    }
    val more = if (all != null && window.startFor(all.size, ROWS_SHOWN) + ROWS_SHOWN < all.size) "More channels below" else null

    ScreenFrame(heading = "Channels", hints = hints, more = more) {
        if (all == null) {
            // Loading is not a failure: saying so for the first half-second reads as one.
            if (ui.live !is LiveState.Loading) EmptyState("Live TV isn't available right now", "Check Live TV in Settings.", emptyList())
            return@ScreenFrame
        }
        WindowColumn(all, { _, ch -> ch.id }, window, ROWS_SHOWN, rowPx = 92, gapPx = 12, modifier = Modifier.width(px(800))) { index, ch, slot ->
            ChannelRow(
                ch, ui.nowMs, loader, url,
                onClick = { onPlay(ch) },
                modifier = slot,
                focus = Modifier
                    .focusRequester(rowFocus.getOrPut(ch.id) { FocusRequester() })
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = if (switchFor != null) switchFocus else FocusRequester.Cancel
                        if (index == 0 && bar != null) up = bar.marked
                    },
                mark = if (ch.id in onHome) RowMark.OnHome else null,
                onFocused = {
                    focusedId = ch.id
                    onSwitch = false
                    memory.remember(FocusTarget(ROW_CHANNELS, ch.id, index))
                },
            )
        }
        Column(Modifier.padding(start = px(840)).width(px(880)), verticalArrangement = Arrangement.spacedBy(px(16))) {
            Preview(previewed, ui.nowMs, is24, artReady = artFor == focusedId, loader, url)
            if (switchFor != null) {
                SettingRow(
                    "Show on Home",
                    onClick = { state.switchTarget(focusedId)?.let { state.togglePin(it.id) } },
                    modifier = Modifier.focusRequester(switchFocus).focusProperties {
                        left = focusedRow() ?: FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                    icon = LineIcon.House,
                    ending = RowEnding.Switch(on = switchFor.id in onHome),
                    onFocused = { onSwitch = true },
                )
            }
        }
    }
}

/**
 * The focused channel, large: its programme's artwork (once [artReady]) under the channel's name and what is on. A
 * channel with no artwork shows its logo plate where the picture would be.
 */
@Composable
private fun Preview(ch: Channel?, nowMs: Long, is24: Boolean, artReady: Boolean, loader: ImageLoader, url: (String?) -> String?) {
    Box(Modifier.fillMaxWidth().height(px(495)).clip(RoundedCornerShape(px(24))).background(AtomixColors.Card)) {
        if (ch == null) return@Box
        val on = onAir(ch, nowMs)
        val lines = onAirLines(on, is24)
        val art = url(on.programme?.image)
        var failed by remember(art) { mutableStateOf(false) }
        if (art == null || failed) {
            // Centred in the upper half, clear of the words.
            LogoPlate(ch, url(ch.logo), loader, Modifier.align(Alignment.TopCenter).padding(top = px(56)), widthPx = 240, heightPx = 135, cornerPx = 22)
        } else {
            if (artReady) Artwork(art, loader, 880, 495, BiasAlignment(0f, -0.6f), onError = { failed = true })
            Box(Modifier.fillMaxSize().background(artScrim()))
        }
        NameChip(ch.name, 30, 38, 20, 8, 14, Modifier.padding(start = px(28), top = px(24)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = px(36), end = px(36), bottom = px(30)).fillMaxWidth()) {
            BasicText(lines.title, style = AtomixType.heading().copy(fontSize = pxSp(52), lineHeight = pxSp(64)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = AtomixType.description().copy(fontSize = pxSp(30), lineHeight = pxSp(40), color = AtomixColors.TextBright)
            lines.time?.let { BasicText(it, style = meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            lines.next?.let { BasicText(it, style = meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            lines.progress?.let { ProgressBar(it, 10, Color.White.copy(alpha = 0.26f), Modifier.padding(top = px(16)).fillMaxWidth()) }
        }
    }
}

/** The small mark at the end of a channel row: a house for a channel that is on Home, or "Watching". */
enum class RowMark { OnHome, Watching }

/**
 * One channel in a list (92 high): number, logo plate, name and what is on. [modifier] places the row; [focus] goes
 * on the same element and carries the caller's focus requester and focus rules.
 */
@Composable
fun ChannelRow(
    ch: Channel,
    nowMs: Long,
    loader: ImageLoader,
    url: (String?) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focus: Modifier = Modifier,
    mark: RowMark? = null,
    onFocused: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val title = onAirTitle(onAir(ch, nowMs))
    Row(
        modifier
            .then(focus)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, cornerPx = 18f, glow = FocusGlow.Row)
            .fillMaxWidth()
            .height(px(92))
            .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, RoundedCornerShape(px(18)))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(start = px(22), end = px(26)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(px(22)),
    ) {
        val number = ch.number?.toString().orEmpty()
        // Wide enough for the number's digits, so a hundred-odd channels do not push their logos out of line one by one.
        BasicText(
            number,
            Modifier.width(px(if (number.length <= 2) 34 else 18 * number.length)),
            style = AtomixType.value().copy(color = if (focused) AtomixColors.TextSoft else AtomixColors.Label, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        LogoPlate(ch, url(ch.logo), loader)
        Column(Modifier.weight(1f)) {
            BasicText(ch.name, style = AtomixType.rowLabel().copy(fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(title, style = AtomixType.description().copy(color = if (focused) AtomixColors.TextSoft else AtomixColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (mark) {
            RowMark.OnHome -> Icon(LineIcon.House, AtomixColors.Ice, 30)
            RowMark.Watching -> Row(horizontalArrangement = Arrangement.spacedBy(px(8)), verticalAlignment = Alignment.CenterVertically) {
                Icon(LineIcon.Bars, AtomixColors.Ice, 24)
                BasicText("Watching", style = AtomixType.note().copy(fontWeight = FontWeight.Bold, color = AtomixColors.Ice), maxLines = 1)
            }
            null -> Unit
        }
    }
}
