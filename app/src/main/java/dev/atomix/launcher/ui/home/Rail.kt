package dev.atomix.launcher.ui.home

import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

data class RailEntry(val icon: LineIcon, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** Floating smoked-glass rail: icons only, widening to show labels while it holds focus. */
@Composable
fun Rail(entries: List<RailEntry>, modifier: Modifier = Modifier) {
    var hasFocus by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (hasFocus) px(220) else px(88), tween(180), label = "rail")
    val shape = RoundedCornerShape(px(44))
    Column(
        modifier
            .width(width)
            .onFocusChanged { hasFocus = it.hasFocus }
            .background(AtomixColors.GlassPanel, shape)
            .border(1.dp, AtomixColors.GlassEdge, shape)
            .padding(vertical = px(10), horizontal = px(10)),
        verticalArrangement = Arrangement.spacedBy(px(4)),
    ) {
        entries.forEach { e ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val pill = RoundedCornerShape(50)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(px(64))
                    .alpha(if (e.enabled) 1f else 0.35f)
                    .background(if (focused) Color.White else Color.Transparent, pill)
                    .clickable(interaction, indication = null, enabled = e.enabled, onClick = e.onClick)
                    .padding(start = px(21)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (focused) AtomixColors.DeepSpaceInset else AtomixColors.TextSoft
                Icon(e.icon, tint, 26)
                AnimatedVisibility(hasFocus, enter = fadeIn(tween(150)) + expandHorizontally(), exit = fadeOut(tween(100)) + shrinkHorizontally()) {
                    BasicText(e.label, style = AtomixType.label().copy(color = tint), modifier = Modifier.padding(start = px(16)), maxLines = 1)
                }
            }
        }
    }
}
