package dev.atomix.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.atomix.launcher.data.Item
import dev.atomix.launcher.data.Row
import dev.atomix.launcher.data.progressFraction
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.atomixFocusRing
import dev.atomix.launcher.ui.theme.px

/** The one content row: landscape cards (backdrop, else poster) with an inset progress bar. */
@Composable
fun ContinueRow(
    row: Row,
    imageUrl: (String?) -> String?,
    loader: ImageLoader,
    onFocused: (Item) -> Unit,
    onOpen: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(px(14))) {
        BasicText(
            if (row.key == "continue") "Continue watching" else row.title,
            style = AtomixType.title(),
            modifier = Modifier.padding(start = px(200)),
        )
        LazyRow(
            contentPadding = PaddingValues(start = px(200), end = px(120), top = px(14), bottom = px(14)),
            horizontalArrangement = Arrangement.spacedBy(px(26)),
        ) {
            itemsIndexed(row.items, key = { _, it -> it.id }) { index, item ->
                Card(item, imageUrl, loader, onOpen) {
                    onFocused(item)
                    // Warm the hero for the neighbours so focus moves land on a ready backdrop.
                    listOfNotNull(row.items.getOrNull(index - 1), row.items.getOrNull(index + 1)).forEach { n ->
                        imageUrl(n.backdrop)?.let { loader.enqueue(ImageRequest.Builder(context).data(it).size(1280, 720).build()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(item: Item, imageUrl: (String?) -> String?, loader: ImageLoader, onOpen: (Item) -> Unit, onFocus: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    val shape = RoundedCornerShape(px(18))
    Box(
        Modifier
            .size(px(336), px(189))
            .scale(if (focused) 1.08f else 1f)
            .atomixFocusRing(focused, 18f)
            .clip(shape)
            .background(AtomixColors.DeepSpaceInset)
            .clickable(interaction, indication = null) { onOpen(item) },
    ) {
        val url = imageUrl(item.backdrop) ?: imageUrl(item.poster)
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).size(480, 270).build(),
                imageLoader = loader,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        BasicText(
            item.title,
            style = AtomixType.label().copy(color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = px(16), end = px(16), bottom = px(26)),
        )
        progressFraction(item.progress, item.duration)?.let { f ->
            Box(
                Modifier.align(Alignment.BottomCenter).padding(start = px(14), end = px(14), bottom = px(12))
                    .fillMaxWidth().height(px(4)).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0.02f, 1f)).background(accentOf(item), RoundedCornerShape(50)))
            }
        }
    }
}
