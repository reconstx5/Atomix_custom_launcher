package dev.atomix.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.atomixFocusRing
import dev.atomix.launcher.ui.theme.px

/** Floating smoked-glass dock: pinned apps, a thin divider, then Live TV and Inputs. */
@Composable
fun Dock(
    apps: List<AppEntry>,
    loader: ImageLoader,
    firstFocus: FocusRequester,
    onApp: (AppEntry) -> Unit,
    onLiveTv: () -> Unit,
    onInputs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(px(90))
            .background(AtomixColors.GlassPanel, shape)
            .border(1.dp, AtomixColors.GlassEdge, shape)
            .padding(horizontal = px(14)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(px(6)),
    ) {
        apps.forEachIndexed { i, app ->
            DockItem(app.label, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier, { onApp(app) }) {
                AsyncImage(model = app.icon, imageLoader = loader, contentDescription = null, modifier = Modifier.size(px(48)))
            }
        }
        if (apps.isNotEmpty()) Box(Modifier.padding(horizontal = px(10)).width(1.dp).height(px(44)).background(AtomixColors.GlassEdge))
        DockItem("Live TV", if (apps.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier, onLiveTv) { tint -> Icon(LineIcon.Live, tint, 36) }
        DockItem("Inputs", Modifier, onInputs) { tint -> Icon(LineIcon.Inputs, tint, 36) }
    }
}

@Composable
private fun DockItem(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .scale(if (focused) 1.05f else 1f)
            .atomixFocusRing(focused, 999f)
            .background(if (focused) Color.White else Color.Transparent, shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .height(px(66))
            .padding(start = px(10), end = px(20)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(px(12)),
    ) {
        Box(Modifier.size(px(48)), contentAlignment = Alignment.Center) { icon(if (focused) AtomixColors.DeepSpaceInset else AtomixColors.TextSoft) }
        BasicText(label, style = AtomixType.label().copy(color = if (focused) AtomixColors.DeepSpaceInset else Color.White), maxLines = 1)
    }
}
