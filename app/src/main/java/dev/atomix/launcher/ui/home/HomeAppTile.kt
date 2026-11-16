package dev.atomix.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

@Composable
private fun nameStyle(m: HomeMetrics): TextStyle =
    AtomixType.rowLabel().copy(fontSize = pxSp(m.namePx), lineHeight = pxSp(m.nameLine), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)

/**
 * One app on Home's row, in the size and shape chosen in Settings › Home layout. The caller sets the width.
 *
 * A wide tile holds the app's banner (or its icon) and its name. A full banner is the tile: the banner fills it, the
 * ring goes round it, and the name sits under it outside the ring. An app with no banner shows its icon, centred on
 * the tile colour.
 */
@Composable
fun HomeAppTile(app: AppEntry, loader: ImageLoader, m: HomeMetrics, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(20))
    if (!m.nameUnder) {
        Box(
            modifier
                .onFocusChanged { if (it.isFocused) onFocused() }
                .tvFocus(focused, cornerPx = 20f, glow = FocusGlow.Card)
                .height(px(m.tileH))
                .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, shape)
                .clickable(interaction, indication = null, onClick = onClick),
        ) {
            // 10 above, the artwork's slot, the name's gap and line, 10 below: every tile's art and name line up.
            Column(Modifier.fillMaxSize().padding(horizontal = px(12), vertical = px(10)), horizontalAlignment = Alignment.CenterHorizontally) {
                HomeAppArt(app, loader, m.artSlot)
                if (m.showNames) BasicText(
                    app.label, Modifier.padding(top = px(m.nameGap)).fillMaxWidth(), style = nameStyle(m), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        // The focus handles the caller attached reach the banner below: it is the one thing here that takes focus.
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .tvFocus(focused, cornerPx = 20f, glow = FocusGlow.Card)
                    .fillMaxWidth()
                    .height(px(m.tileH))
                    .clip(shape)
                    .background(if (focused) AtomixColors.Raised else AtomixColors.Tile)
                    .clickable(interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (app.banner != null) AsyncImage(model = app.banner, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else AsyncImage(model = app.icon, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(px(minOf(72, m.tileH - 24))))
            }
            if (m.showNames) BasicText(
                app.label,
                Modifier.padding(top = px(10)).fillMaxWidth(),
                style = nameStyle(m).copy(color = if (focused) AtomixColors.Text else AtomixColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The last tile of "Your apps": opens the full list on the Apps tab. It follows the tiles' size and shape. */
@Composable
fun HomeAllAppsTile(m: HomeMetrics, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(20))
    val icon = minOf(56, m.tileH - 32)
    if (!m.nameUnder) {
        // The apps' frame: the grid in the artwork's slot, "All apps" on the names' line.
        Column(
            modifier
                .tvFocus(focused, cornerPx = 20f, glow = FocusGlow.Card)
                .height(px(m.tileH))
                .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, shape)
                .clickable(interaction, indication = null, onClick = onClick)
                .padding(horizontal = px(12), vertical = px(10)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(px(m.artSlot)), contentAlignment = Alignment.Center) { Icon(LineIcon.Grid, AtomixColors.Text, minOf(56, m.artSlot)) }
            if (m.showNames) BasicText("All apps", Modifier.padding(top = px(m.nameGap)).fillMaxWidth(), style = nameStyle(m), maxLines = 1)
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .tvFocus(focused, cornerPx = 20f, glow = FocusGlow.Card)
                    .fillMaxWidth()
                    .height(px(m.tileH))
                    .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, shape)
                    .clickable(interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { Icon(LineIcon.Grid, AtomixColors.Text, icon) }
            if (m.showNames) BasicText(
                "All apps",
                Modifier.padding(top = px(10)).fillMaxWidth(),
                style = nameStyle(m).copy(color = if (focused) AtomixColors.Text else AtomixColors.TextSecondary),
                maxLines = 1,
            )
        }
    }
}
