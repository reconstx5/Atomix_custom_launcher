package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/**
 * Round icon-only button (60 px). [current] marks the button of the screen that is open: ice with a dark icon.
 */
@Composable
fun IconButton(icon: LineIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, sizePx: Int = 60, current: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ground = when {
        current -> AtomixColors.Ice
        focused -> AtomixColors.Raised
        else -> AtomixColors.RoundButton
    }
    Box(
        modifier
            .semantics { contentDescription = label }
            .tvFocus(focused, cornerPx = sizePx / 2f)
            .size(px(sizePx))
            .background(ground, CircleShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (current) AtomixColors.Ink else AtomixColors.Text, sizePx / 2)
    }
}

/** The panel button, such as "Try again" or "Choose channels": a rounded tile, raised with the ring when focused. */
@Composable
fun ChipButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: LineIcon? = null, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(16))
    Row(
        modifier
            .tvFocus(focused, cornerPx = 16f)
            .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, shape)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = px(64))
            .padding(horizontal = px(26)),
        horizontalArrangement = Arrangement.spacedBy(px(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, AtomixColors.Text, 26)
        BasicText(text, style = AtomixType.button().copy(fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1)
    }
}

/** A calm message with up to two actions, used for empty, offline and error states. */
@Composable
fun EmptyState(title: String, message: String?, actions: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier, first: Modifier = Modifier) {
    Column(modifier.widthIn(max = px(900)), verticalArrangement = Arrangement.spacedBy(px(12))) {
        BasicText(title, style = AtomixType.subheading())
        if (message != null) BasicText(message, style = AtomixType.description(), modifier = Modifier.widthIn(max = px(820)))
        if (actions.isNotEmpty()) Row(Modifier.padding(top = px(16)), horizontalArrangement = Arrangement.spacedBy(px(16))) {
            actions.forEachIndexed { i, (label, action) -> ChipButton(label, action, if (i == 0) first else Modifier) }
        }
    }
}

/** The "Live" badge: a red dot and the word on a faint red chip. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.height(px(44)).background(AtomixColors.LiveRed.copy(alpha = 0.18f), RoundedCornerShape(px(12))).padding(horizontal = px(16)),
        horizontalArrangement = Arrangement.spacedBy(px(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(px(12)).background(AtomixColors.LiveRed, CircleShape))
        BasicText("Live", style = AtomixType.hint().copy(fontWeight = FontWeight.Bold, color = AtomixColors.LiveText))
    }
}
