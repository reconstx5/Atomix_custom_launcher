package dev.atomix.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.atomix.launcher.apps.InputEntry
import dev.atomix.launcher.apps.TUNER_INPUT
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

/** The Inputs screen's icon for [input]: the plug for an HDMI port, the aerial box for the tuner. */
private fun iconFor(input: InputEntry): LineIcon = if (input.id == TUNER_INPUT) LineIcon.Tuner else LineIcon.Plug

/**
 * One input on Home's Inputs row (spec §6.4), in the size and shape of Home's app tiles: the input's icon where an
 * app's banner goes, the input's name where the app's name goes. The caller sets the width. OK switches to it.
 */
@Composable
fun InputTile(input: InputEntry, m: HomeMetrics, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(20))
    val tint = if (focused) AtomixColors.Ice else AtomixColors.Text
    val name = AtomixType.rowLabel().copy(fontSize = pxSp(m.namePx), lineHeight = pxSp(m.nameLine), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    if (!m.nameUnder) {
        Column(
            modifier
                .onFocusChanged { if (it.isFocused) onFocused() }
                .tvFocus(focused, cornerPx = 20f, glow = FocusGlow.Card)
                .height(px(m.tileH))
                .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, shape)
                .clickable(interaction, indication = null, onClick = onClick)
                .padding(horizontal = px(12)),
            verticalArrangement = Arrangement.spacedBy(px(m.nameGap), Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Centred in the banner's place, so the name sits where an app's does.
            Box(Modifier.size(px(m.bannerW), px(m.bannerH)), contentAlignment = Alignment.Center) {
                Icon(iconFor(input), tint, minOf(48, m.bannerH))
            }
            if (m.showNames) BasicText(input.label, style = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .tvFocus(focused, cornerPx = 20f, glow = FocusGlow.Card)
                    .fillMaxWidth()
                    .height(px(m.tileH))
                    .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, shape)
                    .clickable(interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(iconFor(input), tint, minOf(56, m.tileH - 32)) } }
            if (m.showNames) BasicText(
                input.label,
                Modifier.padding(top = px(10)).fillMaxWidth(),
                style = name.copy(color = if (focused) AtomixColors.Text else AtomixColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
