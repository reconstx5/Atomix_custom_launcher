package dev.atomix.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.OnAir
import dev.atomix.launcher.ui.kit.LiveBadge
import dev.atomix.launcher.ui.kit.LogoPlate
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** "8:00–9:00" in the TV's 12/24-hour style. */
fun timeRange(start: Long, end: Long, is24: Boolean): String = "${clockText(start, is24)}–${clockText(end, is24)}"

fun clockText(ms: Long, is24: Boolean): String =
    java.text.SimpleDateFormat(if (is24) "H:mm" else "h:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

/** "8:00–9:00 · 32 min left", or null without guide data. */
fun onAirMeta(on: OnAir, is24: Boolean): String? {
    val p = on.programme ?: return null
    return "${timeRange(p.start, p.end, is24)} · ${on.minutesLeft} min left"
}

/**
 * A 480×270 "On now" card: logo plate, channel number, programme, time left and progress. Focused it adds the LIVE
 * badge and what's next; with no guide data it says so and has no bar. [dimmed] = server unreachable.
 */
@Composable
fun ChannelCard(
    channel: Channel,
    on: OnAir,
    logoUrl: String?,
    loader: ImageLoader,
    is24: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(16))
    Column(
        modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, cornerPx = 16f)
            .size(px(480), px(270))
            .clip(shape)
            .background(if (focused) AtomixColors.SolidPanel else AtomixColors.DeepSpaceInset.copy(alpha = 0.92f), shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .alpha(if (dimmed) 0.5f else 1f)
            .padding(px(24)),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            LogoPlate(channel, logoUrl, loader, widthPx = 142, heightPx = 80)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(px(8))) {
                channel.number?.let { BasicText(it.toString(), style = AtomixType.badge().copy(color = AtomixColors.TextSecondary)) }
                if (focused && !dimmed) LiveBadge()
            }
        }
        Spacer(Modifier.weight(1f))
        val p = on.programme
        BasicText(
            p?.title ?: channel.name,
            style = AtomixType.cardTitle(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            onAirMeta(on, is24) ?: "No programme information",
            style = AtomixType.small(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = px(4)),
        )
        // Focus adds what's next; it never takes away how long is left on the card you are looking at.
        if (focused && on.upNext != null) {
            BasicText(
                "Next: ${on.upNext.title}",
                style = AtomixType.small().copy(color = AtomixColors.TextDim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = px(2)),
            )
        }
        val progress = on.progress
        if (progress != null) {
            Box(Modifier.padding(top = px(14)).fillMaxWidth().height(px(if (focused) 6 else 4)).background(AtomixColors.Track, RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(AtomixColors.Ice, RoundedCornerShape(50)))
            }
        }
    }
}
