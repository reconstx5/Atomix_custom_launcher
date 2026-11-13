package dev.atomix.launcher.livetv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.onAir
import dev.atomix.launcher.ui.home.LogoPlate
import dev.atomix.launcher.ui.home.clockText
import dev.atomix.launcher.ui.home.timeRange
import dev.atomix.launcher.ui.kit.ChipButton
import dev.atomix.launcher.ui.kit.IconButton
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.LiveBadge
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.livetv.ChannelRow
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** Panels over video: the video can't be blurred, so a translucent dark surface with the usual light edge. */
private val OverVideo = Color(0xE6141726)

/** Where the stream is relative to live; [behindMs] > 0 once the viewer has paused or gone back. */
data class LiveInfo(val paused: Boolean, val behindMs: Long)

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
        when {
            failed != null -> ErrorCard(failed, shown, loader, url, actions, Modifier.align(Alignment.Center))
            state is SessionState.Tuning || (state is SessionState.Playing && !ready) || state is SessionState.Idle ->
                Tuning(shown, loader, url, Modifier.align(Alignment.Center))
        }
        if (Layer.Banner in layers) {
            Banner(
                shown, nowMs, is24, loader, url,
                controls = controls && Layer.Controls in layers && failed == null,
                live = live, actions = actions,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = px(116), end = px(116), bottom = px(60)),
            )
        }
        if (Layer.ChannelList in layers) {
            QuickList(pinned, all, listSource, shown, nowMs, is24, loader, url, actions.pick, Modifier.align(Alignment.CenterStart))
        }
        val big = typed.ifEmpty { notice }
        if (big != null) {
            BasicText(
                big,
                Modifier.align(Alignment.TopEnd).padding(top = px(60), end = px(116))
                    .background(OverVideo, RoundedCornerShape(px(20))).padding(horizontal = px(36), vertical = px(20)),
                style = if (typed.isNotEmpty()) AtomixType.spotlight() else AtomixType.rowHeader(),
            )
        }
    }
}

@Composable
private fun Tuning(ch: Channel, loader: ImageLoader, url: (String?) -> String?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(px(24))) {
        LogoPlate(ch, url(ch.logo), loader, Modifier.size(px(240), px(135)))
        BasicText("Tuning…", style = AtomixType.meta())
    }
}

@Composable
private fun ErrorCard(failed: SessionState.Failed, ch: Channel, loader: ImageLoader, url: (String?) -> String?, actions: OverlayActions, modifier: Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(failed) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Column(
        modifier.widthIn(max = px(1000)).background(OverVideo, RoundedCornerShape(px(28)))
            .border(1.dp, AtomixColors.Edge, RoundedCornerShape(px(28))).padding(px(48)),
        verticalArrangement = Arrangement.spacedBy(px(20)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(24))) {
            LogoPlate(ch, url(ch.logo), loader, Modifier.size(px(160), px(90)), logoMax = 128 to 60)
            BasicText(ch.name, style = AtomixType.rowHeader())
        }
        BasicText(failed.message, style = AtomixType.meta())
        Row(Modifier.padding(top = px(12)), horizontalArrangement = Arrangement.spacedBy(px(20))) {
            ChipButton("Try again", actions.retry, Modifier.focusRequester(first))
            ChipButton("Next channel", actions.nextChannel)
            ChipButton("Back to Home", actions.home)
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
    val shape = RoundedCornerShape(px(28))
    Column(
        modifier.fillMaxWidth().background(OverVideo, shape).border(1.dp, AtomixColors.Edge, shape).padding(px(36)),
        verticalArrangement = Arrangement.spacedBy(px(18)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(28))) {
            BasicText(ch.number?.toString() ?: "", style = AtomixType.rowHeader())
            LogoPlate(ch, url(ch.logo), loader, Modifier.size(px(160), px(90)), logoMax = 128 to 60)
            Column(Modifier.weight(1f)) {
                BasicText(ch.name, style = AtomixType.small(), maxLines = 1)
                BasicText(
                    on.programme?.title ?: "No programme information",
                    style = AtomixType.rowHeader(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(clockText(nowMs, is24), style = AtomixType.meta().copy(color = AtomixColors.TextPrimary))
        }
        val p = on.programme
        if (p != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(20))) {
                BasicText(clockText(p.start, is24), style = AtomixType.small())
                Box(Modifier.weight(1f).height(px(6)).clip(RoundedCornerShape(50)).background(AtomixColors.Track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(on.progress ?: 0f).background(AtomixColors.Ice))
                }
                BasicText(clockText(p.end, is24), style = AtomixType.small())
            }
        }
        on.upNext?.let { n ->
            BasicText("Next: ${n.title} · ${timeRange(n.start, n.end, is24)}", style = AtomixType.small(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (controls) ControlsRow(live, actions)
    }
}

@Composable
private fun ControlsRow(live: LiveInfo, actions: OverlayActions) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Row(Modifier.padding(top = px(8)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(24))) {
        IconButton(
            if (live.paused) LineIcon.Play else LineIcon.Pause,
            if (live.paused) "Play" else "Pause",
            actions.togglePause,
            Modifier.focusRequester(first).onPreviewKeyEvent {
                // ◀ on the first button opens the channel list, as on a clean screen.
                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { actions.openList(); true } else false
            },
        )
        IconButton(LineIcon.Back30, "Back 30 seconds", actions.back30)
        if (live.behindMs > BEHIND_SHOWN_MS) ChipButton("−${mmss(live.behindMs)} · Jump to live", actions.jumpToLive)
        else LiveBadge()
    }
}

@Composable
private fun QuickList(
    pinned: List<Channel>,
    all: List<Channel>,
    initial: ZapSource,
    playing: Channel,
    nowMs: Long,
    is24: Boolean,
    loader: ImageLoader,
    url: (String?) -> String?,
    pick: (Channel, ZapSource) -> Unit,
    modifier: Modifier,
) {
    var tab by remember { mutableStateOf(if (pinned.isEmpty()) ZapSource.All else initial) }
    val list = if (tab == ZapSource.Pinned) pinned else all
    val listState = rememberLazyListState()
    val playingFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    val start = list.indexOfFirst { it.id == playing.id }
    LaunchedEffect(tab) {
        if (start > 0) listState.scrollToItem(start)
        withFrameNanos { }
        runCatching { (if (start >= 0) playingFocus else firstFocus).requestFocus() }
    }
    Column(
        modifier.width(px(760)).fillMaxHeight().background(OverVideo).padding(top = px(60), start = px(48), end = px(24)),
        verticalArrangement = Arrangement.spacedBy(px(20)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(px(16))) {
            TabChip("Pinned", tab == ZapSource.Pinned) { tab = ZapSource.Pinned }
            TabChip("All channels", tab == ZapSource.All) { tab = ZapSource.All }
        }
        if (list.isEmpty()) {
            BasicText("No pinned channels yet.", style = AtomixType.small())
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = px(60)), verticalArrangement = Arrangement.spacedBy(px(8))) {
            itemsIndexed(list, key = { _, c -> c.id }) { i, ch ->
                val focus = when {
                    ch.id == playing.id -> Modifier.focusRequester(playingFocus)
                    i == 0 -> Modifier.focusRequester(firstFocus)
                    else -> Modifier
                }
                ChannelRow(ch, nowMs, loader, url, is24, onClick = { pick(ch, tab) }, focus = focus)
            }
        }
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Column(
        Modifier.tvFocus(focused, cornerPx = 999f)
            .background(if (focused) Color.White else Color.Transparent, shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = px(24), vertical = px(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text, style = AtomixType.menu().copy(color = if (focused) AtomixColors.DeepSpace else if (selected) AtomixColors.TextPrimary else AtomixColors.TextSecondary))
        Spacer(Modifier.height(px(6)))
        Box(Modifier.size(px(8)).background(if (selected) AtomixColors.Ice else Color.Transparent, RoundedCornerShape(50)))
    }
}

private const val BEHIND_SHOWN_MS = 10_000L

fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
