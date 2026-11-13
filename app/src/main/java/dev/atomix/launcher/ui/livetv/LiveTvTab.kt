package dev.atomix.launcher.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.livetv.source.onAir
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.shell.HomeUi
import dev.atomix.launcher.ui.home.LogoPlate
import dev.atomix.launcher.ui.home.onAirMeta
import dev.atomix.launcher.ui.kit.ChipButton
import dev.atomix.launcher.ui.kit.EmptyState
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** The Live TV tab's only row; its focus memory is saved as "liveTab". */
const val ROW_CHANNELS = "channels"

/** Where a tab's content starts: just below the top menu (which ends at y 124). */
const val TAB_TOP = 150

/**
 * Every TV channel in number order with what's on. OK plays (zapping through every channel); ▶ reaches the row's
 * ☆ Pin / ★ Pinned button. Focus and scroll come back to the remembered row on every [restoreTick].
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
    val pinned = remember(ready) { ready?.pinned?.map { it.id }?.toSet().orEmpty() }
    val list = rememberLazyListState()
    val rowFocus = remember { HashMap<String, FocusRequester>() }

    LaunchedEffect(restoreTick, all?.size) {
        val ids = all?.map { it.id } ?: return@LaunchedEffect
        val target = memory.resolve(mapOf(ROW_CHANNELS to ids)) ?: return@LaunchedEffect
        withFrameNanos { }
        if (list.layoutInfo.visibleItemsInfo.none { it.key == target.key }) list.scrollToItem(target.index)
        withFrameNanos { }
        runCatching { rowFocus[target.key]?.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(AtomixColors.DeepSpace)) {
        if (all == null) {
            EmptyState("Live TV isn't available right now", "Check Live TV in Settings.", emptyList(), Modifier.padding(start = px(116), top = px(200)))
            return@Box
        }
        // The list starts below the top menu (and is clipped there), so rows never scroll underneath it.
        LazyColumn(
            Modifier.fillMaxSize().padding(top = px(TAB_TOP)),
            state = list,
            contentPadding = PaddingValues(start = px(116), end = px(116), top = px(20), bottom = px(60)),
            verticalArrangement = Arrangement.spacedBy(px(12)),
        ) {
            itemsIndexed(all, key = { _, ch -> ch.id }) { i, ch ->
                val isPinned = ch.id in pinned
                ChannelRow(
                    ch, ui.nowMs, loader, url, is24, { onPlay(ch) },
                    focus = Modifier
                        .focusRequester(rowFocus.getOrPut(ch.id) { FocusRequester() })
                        .onFocusChanged { if (it.isFocused) memory.remember(FocusTarget(ROW_CHANNELS, ch.id, i)) },
                ) {
                    ChipButton(
                        if (isPinned) "Pinned" else "Pin",
                        onClick = { state.togglePin(ch.id) },
                        icon = if (isPinned) LineIcon.StarFilled else LineIcon.Star,
                        modifier = Modifier.width(px(200)),
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelRow(ch: Channel, nowMs: Long, loader: ImageLoader, url: (String?) -> String?, is24: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, focus: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val on = onAir(ch, nowMs)
    Row(
        modifier.fillMaxWidth().height(px(104)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(px(24)),
    ) {
        Row(
            Modifier.weight(1f)
                .then(focus)
                .tvFocus(focused, scale = 1.02f, cornerPx = 16f)
                .background(if (focused) AtomixColors.SolidPanel else Color.Transparent, RoundedCornerShape(px(16)))
                .clickable(interaction, indication = null, onClick = onClick)
                .padding(horizontal = px(20), vertical = px(12)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(px(24)),
        ) {
            BasicText(ch.number?.toString() ?: "", Modifier.width(px(64)), style = AtomixType.small())
            LogoPlate(ch, url(ch.logo), loader, Modifier.height(px(64)).width(px(128)), logoMax = 104 to 44)
            Column(Modifier.weight(1f)) {
                BasicText(ch.name, style = AtomixType.cardTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    on.programme?.let { "${it.title} · ${onAirMeta(on, is24)}" } ?: "No programme information",
                    style = AtomixType.small(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}
