@file:OptIn(ExperimentalComposeUiApi::class) // FocusRequester.Cancel

package dev.atomix.launcher.livetv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.onAir
import dev.atomix.launcher.ui.home.clockText
import dev.atomix.launcher.ui.home.onAirLines
import dev.atomix.launcher.ui.kit.ChipButton
import dev.atomix.launcher.ui.kit.HintBar
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.LiveBadge
import dev.atomix.launcher.ui.kit.LogoPlate
import dev.atomix.launcher.ui.kit.SegmentedPills
import dev.atomix.launcher.ui.kit.Wash
import dev.atomix.launcher.ui.kit.WashKind
import dev.atomix.launcher.ui.kit.WindowColumn
import dev.atomix.launcher.ui.kit.WindowState
import dev.atomix.launcher.ui.kit.hintsFor
import dev.atomix.launcher.ui.livetv.ChannelRow
import dev.atomix.launcher.ui.livetv.RowMark
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

/** Where the stream is relative to live; [behindMs] > 0 once the viewer has paused or gone back. */
data class LiveInfo(val paused: Boolean, val behindMs: Long)

/** Whether the picture is live: playing, and no more than a moment behind. Only then does the banner say "Live". */
fun isLiveNow(live: LiveInfo): Boolean = !live.paused && live.behindMs <= BEHIND_SHOWN_MS

/** What the overlay asks the activity to do. */
class OverlayActions(
    val togglePause: () -> Unit,
    val back30: () -> Unit,
    val jumpToLive: () -> Unit,
    val openList: () -> Unit,
    val pick: (Channel, ZapSource) -> Unit,
    val retry: () -> Unit,
    val nextChannel: () -> Unit,
    val home: () -> Unit,
)

/**
 * Which hints to show over the picture, from what is on screen: the channel list first, then the error card, then
 * the banner, then tuning. A clean picture has none. [failedButton] is the focused button's label on the error card;
 * [listChannel] is the focused row's channel in the list, or null while a pill has focus.
 */
fun playerSpot(failedButton: String?, tuning: Boolean, layers: Set<Layer>, listChannel: String?): HintSpot? = when {
    Layer.ChannelList in layers -> if (listChannel != null) HintSpot.PlayerList(listChannel) else HintSpot.Pill("Close")
    failedButton != null -> HintSpot.PlayerError(failedButton, bannerUp = Layer.Banner in layers)
    Layer.Banner in layers -> HintSpot.PlayerBanner
    tuning -> HintSpot.PlayerTuning
    else -> null
}

/** How many rows of the channel list show at once. */
private const val LIST_ROWS = 8

private const val TRY_AGAIN = "Try again"

/** Everything drawn over the picture. */
@Composable
fun PlayerOverlay(
    state: SessionState,
    shown: Channel,
    ready: Boolean,
    layers: Set<Layer>,
    controls: Boolean,
    live: LiveInfo,
    nowMs: Long,
    is24: Boolean,
    typed: String,
    notice: String?,
    pinned: List<Channel>,
    all: List<Channel>,
    listSource: ZapSource,
    loader: ImageLoader,
    url: (String?) -> String?,
    actions: OverlayActions,
) {
    Box(Modifier.fillMaxSize()) {
        val failed = state as? SessionState.Failed
        val tuning = failed == null && (state is SessionState.Tuning || (state is SessionState.Playing && !ready) || state is SessionState.Idle)
        // The focused button on the error card, and the focused row's channel in the list (null on a pill).
        var failedButton by remember { mutableStateOf(TRY_AGAIN) }
        var listChannel by remember { mutableStateOf<String?>(null) }
        val spot = playerSpot(if (failed != null) failedButton else null, tuning, layers, listChannel)

        if (failed != null || tuning) Wash(WashKind.Player)
        when {
            failed != null -> ErrorCard(failed, shown, loader, url, actions, onButton = { failedButton = it })
            tuning -> Tuning(shown, loader, url)
        }
        if (Layer.Banner in layers) {
            // Darkens the lower half only, so the banner's words read over a bright picture.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.52f).background(
                    Brush.verticalGradient(0f to Scrim.copy(alpha = 0f), 0.654f to Scrim.copy(alpha = 0.80f), 1f to Scrim.copy(alpha = 0.94f)),
                ),
            )
            Banner(
                shown, nowMs, is24, loader, url,
                controls = controls && Layer.Controls in layers && failed == null,
                live = live, actions = actions,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = px(100), end = px(100), bottom = px(124)),
            )
        }
        if (Layer.ChannelList in layers) {
            QuickList(
                pinned, all, listSource, shown, nowMs, loader, url, actions.pick,
                onFocus = { listChannel = it },
                hints = spot,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        } else if (spot != null) {
            HintBar(hintsFor(spot), Modifier.align(Alignment.BottomEnd).padding(end = px(100), bottom = px(40)))
        }
        if (typed.isNotEmpty()) NumberChip(typed, numberMatch(typed, all), nowMs, loader, url, Modifier.align(Alignment.TopEnd))
        else if (notice != null) Notice(notice, Modifier.align(Alignment.TopEnd))
    }
}

private val Scrim = Color(0xFF070E1A)

/** "Channel 2", or nothing for a channel without a number. */
@Composable
private fun ChannelNumberLine(ch: Channel) {
    ch.number?.let { BasicText("Channel $it", style = AtomixType.description().copy(fontSize = pxSp(26), lineHeight = pxSp(34)), maxLines = 1) }
}

@Composable
private fun Tuning(ch: Channel, loader: ImageLoader, url: (String?) -> String?) {
    Column(Modifier.fillMaxWidth().height(px(1000)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LogoPlate(ch, url(ch.logo), loader, widthPx = 240, heightPx = 135, cornerPx = 22)
        Spacer(Modifier.height(px(28)))
        ChannelNumberLine(ch)
        BasicText(ch.name, style = AtomixType.heading(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(px(20)))
        // A still arc: a spinning one would be an animation that runs for as long as tuning takes.
        Row(horizontalArrangement = Arrangement.spacedBy(px(14)), verticalAlignment = Alignment.CenterVertically) {
            Icon(LineIcon.Arc, AtomixColors.Ice, 28)
            BasicText("Tuning…", style = AtomixType.rowLabel().copy(fontWeight = FontWeight.Normal, lineHeight = pxSp(38), color = AtomixColors.TextSoft))
        }
    }
}

@Composable
private fun ErrorCard(
    failed: SessionState.Failed,
    ch: Channel,
    loader: ImageLoader,
    url: (String?) -> String?,
    actions: OverlayActions,
    onButton: (String) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(failed) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Column(
        Modifier.padding(start = px(460), top = px(330)).width(px(1000))
            .background(AtomixColors.Panel, RoundedCornerShape(px(28)))
            .padding(start = px(48), top = px(44), end = px(48), bottom = px(48)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(24))) {
            LogoPlate(ch, url(ch.logo), loader, widthPx = 132, heightPx = 74, cornerPx = 12)
            Column {
                ChannelNumberLine(ch)
                BasicText(ch.name, style = AtomixType.heading().copy(fontSize = pxSp(40), lineHeight = pxSp(50)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        BasicText(
            failed.message,
            Modifier.padding(top = px(28)),
            style = AtomixType.description().copy(fontSize = pxSp(30), lineHeight = pxSp(42), color = AtomixColors.TextBright),
        )
        Row(Modifier.padding(top = px(34)), horizontalArrangement = Arrangement.spacedBy(px(16))) {
            @Composable
            fun button(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
                ChipButton(label, onClick, modifier.height(px(68)).onFocusChanged { if (it.isFocused) onButton(label) })
            button(TRY_AGAIN, actions.retry, Modifier.focusRequester(first))
            button("Next channel", actions.nextChannel)
            button("Back to Home", actions.home)
        }
    }
}

@Composable
private fun Banner(
    ch: Channel,
    nowMs: Long,
    is24: Boolean,
    loader: ImageLoader,
    url: (String?) -> String?,
    controls: Boolean,
    live: LiveInfo,
    actions: OverlayActions,
    modifier: Modifier,
) {
    val on = onAir(ch, nowMs)
    val lines = onAirLines(on, is24)
    Column(
        modifier.fillMaxWidth().background(AtomixColors.OverVideo, RoundedCornerShape(px(24)))
            .padding(start = px(36), top = px(30), end = px(36), bottom = px(32)),
        verticalArrangement = Arrangement.spacedBy(px(22)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(24))) {
            val number = ch.number?.toString().orEmpty()
            BasicText(
                number,
                Modifier.width(px(if (number.length <= 2) 34 else 20 * number.length)),
                style = AtomixType.subheading().copy(color = AtomixColors.TextSoft, textAlign = TextAlign.Center),
                maxLines = 1,
            )
            LogoPlate(ch, url(ch.logo), loader, widthPx = 132, heightPx = 74, cornerPx = 14)
            Column(Modifier.weight(1f)) {
                BasicText(ch.name, style = AtomixType.description().copy(fontSize = pxSp(26), lineHeight = pxSp(34)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(lines.title, style = AtomixType.heading().copy(lineHeight = pxSp(54)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicText(clockText(nowMs, is24), style = AtomixType.clock(), maxLines = 1)
        }
        val p = on.programme
        if (p != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(20))) {
                val time = AtomixType.value().copy(fontWeight = FontWeight.Medium, color = AtomixColors.TextBright)
                BasicText(clockText(p.start, is24), style = time)
                Box(Modifier.weight(1f).height(px(10)).background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(px(5)))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(on.progress ?: 0f).background(AtomixColors.Ice, RoundedCornerShape(px(5))))
                }
                BasicText(clockText(p.end, is24), style = time)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(16))) {
            val behind = controls && live.behindMs > BEHIND_SHOWN_MS
            if (controls) {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
                ChipButton(
                    if (live.paused) "Play" else "Pause",
                    actions.togglePause,
                    Modifier.focusRequester(first).onPreviewKeyEvent {
                        // ◀ on the first button opens the channel list, as on a clean screen.
                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { actions.openList(); true } else false
                    },
                    icon = if (live.paused) LineIcon.Play else LineIcon.Pause,
                )
                ChipButton("Back 30 seconds", actions.back30, icon = LineIcon.Back30)
            }
            if (behind) ChipButton("Jump to live", actions.jumpToLive, lead = "−${mmss(live.behindMs)}")
            // Paused or behind with the buttons put away: no badge, rather than "Live" over a picture that is not.
            else if (isLiveNow(live)) LiveBadge(Modifier.padding(start = px(if (controls) 8 else 0)))
            val next = on.upNext?.let { "Next: ${it.title} • ${clockText(it.start, is24)}" }.orEmpty()
            BasicText(
                next,
                Modifier.weight(1f),
                style = AtomixType.value().copy(fontWeight = FontWeight.Normal, color = AtomixColors.TextSoft, textAlign = TextAlign.End),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The channel list over the left of the picture: "On Home" or every channel, the one being watched marked. */
@Composable
private fun QuickList(
    pinned: List<Channel>,
    all: List<Channel>,
    initial: ZapSource,
    playing: Channel,
    nowMs: Long,
    loader: ImageLoader,
    url: (String?) -> String?,
    pick: (Channel, ZapSource) -> Unit,
    onFocus: (String?) -> Unit,
    hints: HintSpot?,
    modifier: Modifier,
) {
    var tab by remember { mutableStateOf(if (pinned.isEmpty()) ZapSource.All else initial) }
    val list = if (tab == ZapSource.Pinned) pinned else all
    val window = remember(tab) { WindowState() }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    // Opening the list, or choosing the other one, puts focus on the channel being watched (else the first row).
    LaunchedEffect(tab) {
        val start = list.indexOfFirst { it.id == playing.id }.coerceAtLeast(0)
        val target = list.getOrNull(start) ?: return@LaunchedEffect
        window.show(start, list.size, LIST_ROWS)
        withFrameNanos { }
        runCatching { rowFocus[target.id]?.requestFocus() }
    }
    Column(
        modifier.width(px(800)).fillMaxHeight().background(AtomixColors.OverVideoList)
            .padding(start = px(60), top = px(40), end = px(40), bottom = px(40)),
    ) {
        SegmentedPills(
            listOf("On Home", "All channels"),
            selected = if (tab == ZapSource.Pinned) 0 else 1,
            onSelect = { tab = if (it == 0) ZapSource.Pinned else ZapSource.All },
            onFocused = { onFocus(null) },
            trackPx = 60, padPx = 6, pillPadPx = 22, gapPx = 6,
        )
        Spacer(Modifier.height(px(22)))
        if (list.isEmpty()) BasicText("No channels on Home yet.", style = AtomixType.description())
        WindowColumn(list, { _, ch -> ch.id }, window, LIST_ROWS, rowPx = 92, gapPx = 10) { _, ch, slot ->
            ChannelRow(
                ch, nowMs, loader, url,
                onClick = { pick(ch, tab) },
                modifier = slot,
                focus = Modifier.focusRequester(rowFocus.getOrPut(ch.id) { FocusRequester() })
                    .focusProperties { left = FocusRequester.Cancel; right = FocusRequester.Cancel },
                mark = if (ch.id == playing.id) RowMark.Watching else null,
                onFocused = { onFocus(ch.name) },
            )
        }
        Spacer(Modifier.weight(1f))
        if (hints != null) HintBar(hintsFor(hints))
    }
}

/** The digits being typed and, once they match a channel, that channel beside them. */
@Composable
private fun NumberChip(typed: String, match: Channel?, nowMs: Long, loader: ImageLoader, url: (String?) -> String?, modifier: Modifier) {
    Row(
        modifier.padding(top = px(60), end = px(100)).background(AtomixColors.OverVideo, RoundedCornerShape(px(24)))
            .padding(start = px(32), top = px(22), end = px(36), bottom = px(22)),
        horizontalArrangement = Arrangement.spacedBy(px(28)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(typed, style = AtomixType.heading().copy(fontSize = pxSp(96), lineHeight = pxSp(104), color = AtomixColors.Ice), maxLines = 1)
        if (match != null) {
            LogoPlate(match, url(match.logo), loader, widthPx = 132, heightPx = 74, cornerPx = 14)
            Column {
                BasicText(match.name, style = AtomixType.heading().copy(fontSize = pxSp(36), lineHeight = pxSp(46)), maxLines = 1)
                BasicText(
                    onAirLines(onAir(match, nowMs), is24 = true).title,
                    style = AtomixType.description().copy(fontSize = pxSp(26), lineHeight = pxSp(34), color = AtomixColors.TextSoft),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A short notice in the number chip's place, such as "No channel 9999". */
@Composable
private fun Notice(text: String, modifier: Modifier) {
    BasicText(
        text,
        modifier.padding(top = px(60), end = px(100)).background(AtomixColors.OverVideo, RoundedCornerShape(px(24)))
            .padding(start = px(32), top = px(22), end = px(36), bottom = px(22)),
        style = AtomixType.heading().copy(fontSize = pxSp(36), lineHeight = pxSp(46)),
    )
}

private const val BEHIND_SHOWN_MS = 10_000L

fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
