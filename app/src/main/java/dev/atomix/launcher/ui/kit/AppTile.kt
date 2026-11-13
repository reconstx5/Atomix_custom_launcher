package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

/** The two sizes an app tile is drawn at: on Home's row and in the Apps grid. */
enum class AppTileSize(val height: Int, val bannerW: Int, val bannerH: Int, val nameSp: Int, val nameLine: Int) {
    Home(141, 112, 63, 28, 34),
    Grid(164, 128, 72, 22, 28),
}

/** The app's banner, or its icon when it has none, in the banner's box. */
@Composable
fun AppArt(app: AppEntry, loader: ImageLoader, widthPx: Int, heightPx: Int, cornerPx: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(px(widthPx), px(heightPx)).clip(RoundedCornerShape(px(cornerPx))).background(AtomixColors.Inset), contentAlignment = Alignment.Center) {
        if (app.banner != null) {
            AsyncImage(model = app.banner, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(model = app.icon, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(px(4)))
        }
    }
}

/**
 * One app: its banner (or icon) over its name. The caller sets the width. [onHome] adds the small house that marks
 * an app as being in "Your apps".
 */
@Composable
fun AppTile(
    app: AppEntry,
    loader: ImageLoader,
    size: AppTileSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHome: Boolean = false,
    onFocused: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(20))
    Box(
        modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, cornerPx = 20f, glow = FocusGlow.Card)
            .height(px(size.height))
            .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, shape)
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = px(12)),
            verticalArrangement = Arrangement.spacedBy(px(10), Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppArt(app, loader, size.bannerW, size.bannerH, 10)
            BasicText(
                app.label,
                style = AtomixType.rowLabel().copy(fontSize = pxSp(size.nameSp), lineHeight = pxSp(size.nameLine), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onHome) Icon(LineIcon.House, AtomixColors.Ice, 26, Modifier.align(Alignment.TopEnd).padding(top = px(12), end = px(12)))
    }
}
