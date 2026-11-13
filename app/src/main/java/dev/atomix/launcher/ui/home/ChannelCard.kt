package dev.atomix.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.OnAir
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

/** "8:00–9:00" in the TV's 12/24-hour style. */
fun timeRange(start: Long, end: Long, is24: Boolean): String = "${clockText(start, is24)}–${clockText(end, is24)}"

/** "07:36" on a 24-hour TV, "7:36" on a 12-hour one. */
fun clockText(ms: Long, is24: Boolean): String =
    java.text.SimpleDateFormat(if (is24) "HH:mm" else "h:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

/**
 * The lines a card, the Live TV preview and the player banner say about what a channel is showing. Without a
 * programme (no guide data, or the guide has run out while the screen sat open) there is only the title line, so
 * nothing can show a stale time, negative minutes or a full bar.
 */
data class OnAirLines(val title: String, val time: String?, val next: String?, val progress: Float?)

fun onAirLines(on: OnAir, is24: Boolean): OnAirLines {
    val p = on.programme ?: return OnAirLines("No programme information", null, null, null)
    return OnAirLines(
        title = p.title,
        time = "${timeRange(p.start, p.end, is24)} • ${on.minutesLeft} min left",
        next = on.upNext?.let { "Next: ${it.title}" },
        progress = on.progress,
    )
}

/** The sizes of the two "On now" cards, px at 1920. */
const val LARGE_CARD_W = 1050
const val LARGE_CARD_H = 506
const val SMALL_CARD_W = 646
const val SMALL_CARD_H = 244

/** The dark colour artwork fades into. */
private val Scrim = Color(0xFF070E1A)

/** The fade over full-bleed artwork: a little dark at the top for the channel's name, nearly solid behind the words. */
internal fun artScrim(): Brush = Brush.verticalGradient(
    0f to Scrim.copy(alpha = 0.25f), 0.26f to Scrim.copy(alpha = 0f), 0.52f to Scrim.copy(alpha = 0.55f), 1f to Scrim.copy(alpha = 0.96f),
)

/** Programme artwork, asked for at the size it is drawn and cropped to fill; nothing is drawn without a picture. */
@Composable
internal fun Artwork(url: String?, loader: ImageLoader, widthPx: Int, heightPx: Int, alignment: Alignment, modifier: Modifier = Modifier, onError: () -> Unit = {}) {
    if (url == null) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val w = with(density) { px(widthPx).roundToPx() }
    val h = with(density) { px(heightPx).roundToPx() }
    val failed by rememberUpdatedState(onError)
    val request = remember(url, w, h) { ImageRequest.Builder(context).data(url).size(w, h).listener(onError = { _, _ -> failed() }).build() }
    AsyncImage(request, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Crop, alignment = alignment, modifier = modifier.size(px(widthPx), px(heightPx)))
}

/** The channel's name on a dark chip, so it reads over any artwork. */
@Composable
internal fun NameChip(name: String, sizeSp: Int, lineSp: Int, padH: Int, padV: Int, cornerPx: Int, modifier: Modifier = Modifier) {
    BasicText(
        name,
        modifier.background(AtomixColors.HintChip, RoundedCornerShape(px(cornerPx))).padding(horizontal = px(padH), vertical = px(padV)),
        style = AtomixType.heading().copy(fontSize = pxSp(sizeSp), lineHeight = pxSp(lineSp)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun ProgressBar(progress: Float, heightPx: Int, track: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(px(heightPx)).background(track, RoundedCornerShape(px(5)))) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(AtomixColors.Ice, RoundedCornerShape(px(5))))
    }
}

/**
 * One "On now" card: the large one (1050×506) when [large], else a small one (646×244). It is one card either way,
 * so a channel that moves between the large slot and a small one while the row is rearranged keeps its focus.
 * [watchHint] is false while the row is being rearranged, when OK does not watch.
 */
@Composable
fun ChannelCard(
    channel: Channel,
    lines: OnAirLines,
    artUrl: String?,
    loader: ImageLoader,
    large: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    watchHint: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val corner = if (large) 24 else 22
    // Artwork covers the large card; the small card's own colour shows on its left half, so that is what is raised.
    val ground = if (focused && (!large || artUrl == null)) AtomixColors.Raised else AtomixColors.Card
    Box(
        modifier
            // The focused card's glow lies over its neighbours, not under them.
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, cornerPx = corner.toFloat(), glow = FocusGlow.Card)
            .size(px(if (large) LARGE_CARD_W else SMALL_CARD_W), px(if (large) LARGE_CARD_H else SMALL_CARD_H))
            .clip(RoundedCornerShape(px(corner)))
            .background(ground)
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        if (large) LargeChannelCard(channel, lines, artUrl, loader, watchHint = focused && watchHint)
        else SmallChannelCard(channel, lines, artUrl, loader, ground)
    }
}

/**
 * What the large card shows: the programme's artwork under a dark fade, the channel's name, the programme lines and,
 * with [watchHint], the one hint Home carries at rest: "OK Watch".
 */
@Composable
fun BoxScope.LargeChannelCard(channel: Channel, lines: OnAirLines, artUrl: String?, loader: ImageLoader, watchHint: Boolean) {
    Artwork(artUrl, loader, LARGE_CARD_W, LARGE_CARD_H, BiasAlignment(0f, -0.6f))
    Box(Modifier.fillMaxSize().background(artScrim()))
    NameChip(channel.name, 30, 38, 20, 8, 14, Modifier.padding(start = px(28), top = px(24)))
    Column(Modifier.align(Alignment.BottomStart).padding(start = px(36), bottom = px(30)).width(px(720))) {
        // A long title takes a second line rather than being cut short: this is read from across the room.
        BasicText(lines.title, style = AtomixType.heading().copy(fontSize = pxSp(64), lineHeight = pxSp(74)), maxLines = 2, overflow = TextOverflow.Ellipsis)
        val meta = AtomixType.description().copy(fontSize = pxSp(32), lineHeight = pxSp(42), color = AtomixColors.TextBright)
        lines.time?.let { BasicText(it, style = meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        lines.next?.let { BasicText(it, style = meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        lines.progress?.let { ProgressBar(it, 10, Color.White.copy(alpha = 0.26f), Modifier.padding(top = px(16)).fillMaxWidth()) }
    }
    if (watchHint) {
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = px(24), bottom = px(26)).height(px(64))
                .background(AtomixColors.HintChip, RoundedCornerShape(px(16))).padding(horizontal = px(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = AtomixType.rowLabel().copy(fontWeight = FontWeight.Bold)
            Box(Modifier.height(px(48)).background(AtomixColors.KeyCap, RoundedCornerShape(px(12))).padding(horizontal = px(16)), contentAlignment = Alignment.Center) {
                BasicText("OK", style = style)
            }
            BasicText("Watch", Modifier.padding(horizontal = px(18)), style = style)
        }
    }
}

/** What a small card shows: words on the left over [ground], the programme's artwork fading in from the right. */
@Composable
fun BoxScope.SmallChannelCard(channel: Channel, lines: OnAirLines, artUrl: String?, loader: ImageLoader, ground: Color) {
    Artwork(artUrl, loader, 370, SMALL_CARD_H, Alignment.Center, Modifier.align(Alignment.CenterEnd))
    if (artUrl != null) Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to ground, 0.43f to ground, 0.62f to ground.copy(alpha = 0.55f), 0.82f to ground.copy(alpha = 0f)),
        ),
    )
    NameChip(channel.name, 26, 32, 16, 6, 12, Modifier.padding(start = px(28), top = px(22)))
    Column(Modifier.padding(start = px(30), top = px(82)).width(px(330))) {
        BasicText(lines.title, style = AtomixType.heading().copy(fontSize = pxSp(32), lineHeight = pxSp(46)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val meta = AtomixType.description().copy(fontSize = pxSp(26), lineHeight = pxSp(34), color = AtomixColors.TextSoft)
        lines.time?.let { BasicText(it, style = meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        lines.next?.let { BasicText(it, style = meta, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        lines.progress?.let { ProgressBar(it, 9, AtomixColors.Track, Modifier.padding(top = px(14)).width(px(300))) }
    }
}
