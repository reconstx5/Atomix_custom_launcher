package dev.atomix.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.ui.kit.ChipButton
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** One favourite app: icon (56 px) and name (26 px) in a glass pill; white when focused. */
@Composable
fun AppPill(app: AppEntry, loader: ImageLoader, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, scale = 1.1f, cornerPx = 999f)
            .background(if (focused) Color.White else AtomixColors.GlassClear, shape)
            .border(1.dp, if (focused) Color.Transparent else AtomixColors.Edge, shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .height(px(88))
            .padding(start = px(16), end = px(28)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(px(14)),
    ) {
        AsyncImage(model = app.icon, imageLoader = loader, contentDescription = null, modifier = Modifier.size(px(56)))
        BasicText(app.label, style = AtomixType.small().copy(fontSize = AtomixType.menu().fontSize * (26f / 28f), color = if (focused) AtomixColors.DeepSpace else AtomixColors.TextPrimary), maxLines = 1)
    }
}

/** Favourite apps in saved order, ending with All apps. */
@Composable
fun AppsRow(
    apps: List<AppEntry>,
    loader: ImageLoader,
    listState: LazyListState,
    requesters: (String) -> FocusRequester,
    onOpen: (AppEntry) -> Unit,
    onFocused: (AppEntry, Int) -> Unit,
    onAllApps: () -> Unit,
    allAppsFocus: FocusRequester,
    modifier: Modifier = Modifier,
    held: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    LazyRow(
        modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = px(116), vertical = px(16)),
        horizontalArrangement = Arrangement.spacedBy(px(36)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
            AppPill(app, loader, { onOpen(app) }, { onFocused(app, i) }, Modifier.lifted(app.packageName == held).focusRequester(requesters(app.packageName)))
        }
        if (trailing != null) item(key = "edit-trailing") { trailing() }
        else {
            item(key = "all-apps") {
                ChipButton("All apps", onAllApps, Modifier.focusRequester(allAppsFocus).height(px(88)), icon = LineIcon.Apps)
            }
            if (onEdit != null) item(key = "edit") {
                ChipButton("Edit", onEdit, Modifier.height(px(88)), icon = LineIcon.Edit)
            }
        }
    }
}
