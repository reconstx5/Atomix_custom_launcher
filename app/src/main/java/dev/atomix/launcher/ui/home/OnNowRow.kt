package dev.atomix.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import coil.ImageLoader
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.onAir
import dev.atomix.launcher.ui.theme.px

/** Pinned TV channels as 480×270 cards, 40 px apart: three full cards and a peek of the fourth. */
@Composable
fun OnNowRow(
    channels: List<Channel>,
    nowMs: Long,
    dimmed: Boolean,
    loader: ImageLoader,
    logoUrl: (Channel) -> String?,
    is24: Boolean,
    listState: LazyListState,
    requesters: (String) -> FocusRequester,
    onPlay: (Channel) -> Unit,
    onFocused: (Channel, Int) -> Unit,
    modifier: Modifier = Modifier,
    held: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    LazyRow(
        modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = px(116), vertical = px(24)),
        horizontalArrangement = Arrangement.spacedBy(px(40)),
    ) {
        itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
            ChannelCard(
                channel = ch,
                on = onAir(ch, nowMs),
                logoUrl = logoUrl(ch),
                loader = loader,
                is24 = is24,
                dimmed = dimmed,
                onClick = { onPlay(ch) },
                onFocused = { onFocused(ch, i) },
                modifier = Modifier.lifted(ch.id == held).focusRequester(requesters(ch.id)),
            )
        }
        if (trailing != null) item(key = "edit-trailing") {
            Box(Modifier.height(px(270)), contentAlignment = Alignment.Center) { trailing() }
        }
    }
}
