package dev.atomix.launcher.ui.apps

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.atomixFocusRing
import dev.atomix.launcher.ui.theme.onLongPressOk
import dev.atomix.launcher.ui.theme.px

/** Every launchable app as a 16:9 banner tile; OK opens, holding OK pins or unpins it from the dock. */
@Composable
fun AppsScreen(state: AppsScreenState, loader: ImageLoader, onLaunch: (AppEntry) -> Unit, onDockChanged: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var version by remember { mutableIntStateOf(0) }
    val apps = remember { state.list() }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(AtomixColors.DeepSpace)) {
        Column(Modifier.fillMaxSize().padding(top = px(56))) {
            BasicText("Apps", style = AtomixType.headline(), modifier = Modifier.padding(start = px(96), bottom = px(24)))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(start = px(96), end = px(96), top = px(16), bottom = px(64)),
                horizontalArrangement = Arrangement.spacedBy(px(28)),
                verticalArrangement = Arrangement.spacedBy(px(36)),
            ) {
                itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                    AppTile(app, loader, if (i == 0) Modifier.focusRequester(first) else Modifier, onClick = { onLaunch(app) }, onLongPress = { menuFor = app })
                }
            }
        }
        menuFor?.let { app ->
            val pinned = remember(version, app) { state.inDock(app.packageName) }
            GlassMenu(
                app.label,
                listOf(MenuOption(if (pinned) "Remove from dock" else "Add to dock") {
                    state.toggleDock(app.packageName); version++; onDockChanged()
                }),
                onDismiss = { menuFor = null },
            )
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, loader: ImageLoader, modifier: Modifier, onClick: () -> Unit, onLongPress: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(18))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .scale(if (focused) 1.08f else 1f)
                .atomixFocusRing(focused, 18f)
                .clip(shape)
                .background(AtomixColors.GlassClear)
                .border(1.dp, AtomixColors.GlassEdge, shape)
                .onLongPressOk(onLongPress)
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
            modifier = Modifier.padding(top = px(14)),
        )
    }
}
