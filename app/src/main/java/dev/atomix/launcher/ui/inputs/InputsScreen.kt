package dev.atomix.launcher.ui.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.atomix.launcher.apps.InputEntry
import dev.atomix.launcher.ui.kit.FocusGlow
import dev.atomix.launcher.ui.kit.HintSpot
import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.kit.ScreenFrame
import dev.atomix.launcher.ui.kit.hintsFor
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

/** Four cards across the 1720 px page, 24 apart. */
private const val CARD_W = (1720 - 3 * 24) / 4

/**
 * The TV's inputs as a page: one card per HDMI port, with the label the TV gives it, and under them the TV tuner.
 * OK switches to it. What is plugged in, whether it is on, renaming and hiding are not known or offered yet, so the
 * card leaves their places empty rather than showing a state it cannot tell.
 */
@Composable
fun InputsScreen(hdmi: List<InputEntry>, tuner: InputEntry, onPick: (InputEntry) -> Unit) {
    val first = remember { FocusRequester() }
    var focused by remember { mutableStateOf<InputEntry?>(null) }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }

    ScreenFrame(heading = "Inputs", hints = focused?.let { hintsFor(HintSpot.InputCard(it.label)) }.orEmpty()) {
        Column(Modifier.padding(top = px(8))) {
            if (hdmi.isEmpty()) {
                BasicText("No HDMI inputs found.", style = AtomixType.description())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(px(24))) {
                    hdmi.forEachIndexed { i, input ->
                        InputCard(input.label, { onPick(input) }, { focused = input }, if (i == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
            BasicText("Other sources", Modifier.padding(top = px(30)), style = AtomixType.subheading())
            SourceTile(tuner.label, { onPick(tuner) }, { focused = tuner }, Modifier.padding(top = px(14)).then(if (hdmi.isEmpty()) Modifier.focusRequester(first) else Modifier))
        }
    }
}

/** One HDMI port (330 high): the plug in its circle where the board has it, and the port's label under it. */
@Composable
private fun InputCard(label: String, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, cornerPx = 22f, glow = FocusGlow.Card)
            .size(px(CARD_W), px(330))
            .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, RoundedCornerShape(px(22)))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = px(28), vertical = px(26)),
    ) {
        // The status chip's place, kept empty, so the circle sits 90 below the card's top as it will with a chip.
        Spacer(Modifier.height(px(90 - 26)))
        Box(
            Modifier.size(px(100)).background(if (focused) AtomixColors.Ice.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LineIcon.Plug, if (focused) AtomixColors.Ice else AtomixColors.Text, 54)
        }
        BasicText(
            label,
            Modifier.padding(top = px(20)).fillMaxWidth(),
            style = AtomixType.heading().copy(fontSize = pxSp(38), lineHeight = pxSp(46)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A source that is not an HDMI port (100 high, a quarter of the page wide): the TV tuner. */
@Composable
private fun SourceTile(label: String, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvFocus(focused, cornerPx = 20f, glow = FocusGlow.Card)
            .size(px(CARD_W), px(100))
            .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, RoundedCornerShape(px(20)))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = px(26)),
        horizontalArrangement = Arrangement.spacedBy(px(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LineIcon.Tuner, AtomixColors.Text, 40)
        BasicText(label, style = AtomixType.rowLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
