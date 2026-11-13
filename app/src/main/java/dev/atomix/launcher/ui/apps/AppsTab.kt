package dev.atomix.launcher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.shell.FocusMemory
import dev.atomix.launcher.shell.FocusTarget
import dev.atomix.launcher.ui.kit.ChipButton
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.livetv.TAB_TOP
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** The Apps tab's only row; its focus memory is saved as "appsTab". */
const val ROW_ALL_APPS = "all"

/**
 * Every launchable app as a 16:9 tile; OK opens it, ▼ reaches its ☆ Add to Home / ★ On Home button. Focus and
 * scroll come back to the remembered tile on every [restoreTick].
 */
@Composable
fun AppsTab(
    state: AppsTabState,
    loader: ImageLoader,
    memory: FocusMemory,
    restoreTick: Int,
    onLaunch: (AppEntry) -> Unit,
    onFavouritesChanged: () -> Unit,
) {
    val apps by state.apps.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    val grid = rememberLazyGridState()
    val tileFocus = remember { HashMap<String, FocusRequester>() }

    LaunchedEffect(restoreTick, apps.size) {
        val target = memory.resolve(mapOf(ROW_ALL_APPS to apps.map { it.packageName })) ?: return@LaunchedEffect
        withFrameNanos { }
        if (grid.layoutInfo.visibleItemsInfo.none { it.key == target.key }) grid.scrollToItem(target.index)
        withFrameNanos { }
        runCatching { tileFocus[target.key]?.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = grid,
        // Starts below the top menu (and is clipped there), so tiles never scroll underneath it.
        modifier = Modifier.fillMaxSize().background(AtomixColors.DeepSpace).padding(top = px(TAB_TOP)),
        contentPadding = PaddingValues(start = px(116), end = px(116), top = px(20), bottom = px(64)),
        horizontalArrangement = Arrangement.spacedBy(px(28)),
        verticalArrangement = Arrangement.spacedBy(px(40)),
    ) {
        itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
            val onHome = remember(version, app.packageName) { state.isFavourite(app.packageName) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppTile(
                    app, loader,
                    Modifier
                        .focusRequester(tileFocus.getOrPut(app.packageName) { FocusRequester() })
                        .onFocusChanged { if (it.isFocused) memory.remember(FocusTarget(ROW_ALL_APPS, app.packageName, i)) },
                    onClick = { onLaunch(app) },
                )
                ChipButton(
                    if (onHome) "On Home" else "Add to Home",
                    onClick = { state.toggle(app.packageName); version++; onFavouritesChanged() },
                    icon = if (onHome) LineIcon.StarFilled else LineIcon.Star,
                    modifier = Modifier.padding(top = px(12)),
                )
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, loader: ImageLoader, focus: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(18))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(focus)
                .tvFocus(focused, cornerPx = 18f)
                .clip(shape)
                .background(AtomixColors.GlassClear)
                .border(1.dp, AtomixColors.GlassEdge, shape)
                .clickable(interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (app.banner != null) {
                AsyncImage(model = app.banner, imageLoader = loader, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(model = app.icon, imageLoader = loader, contentDescription = app.label, modifier = Modifier.size(px(88)))
            }
        }
        BasicText(
            app.label,
            style = AtomixType.label().copy(color = if (focused) AtomixColors.Text else AtomixColors.TextSoft),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Clear of the focused tile's 8 % growth and ring.
            modifier = Modifier.padding(top = px(22)),
        )
    }
}
