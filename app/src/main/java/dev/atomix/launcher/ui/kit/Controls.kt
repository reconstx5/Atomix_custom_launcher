package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** Round icon-only button (64 px target): glass at rest, white when focused. */
@Composable
fun IconButton(icon: LineIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, sizePx: Int = 64) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .semantics { contentDescription = label }
            .tvFocus(focused, cornerPx = sizePx / 2f)
            .size(px(sizePx))
            .background(if (focused) Color.White else AtomixColors.GlassClear, CircleShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (focused) AtomixColors.DeepSpace else AtomixColors.TextPrimary, sizePx * 30 / 64)
    }
}

/** Small pill action such as "✎ Edit", "Try again" or "Choose channels". */
@Composable
fun ChipButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: LineIcon? = null, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .tvFocus(focused, cornerPx = 999f)
            .background(if (focused) Color.White else AtomixColors.GlassClear, shape)
            .border(1.dp, if (focused) Color.Transparent else AtomixColors.Edge, shape)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = px(56))
            .padding(horizontal = px(26)),
        horizontalArrangement = Arrangement.spacedBy(px(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (focused) AtomixColors.DeepSpace else AtomixColors.TextPrimary
        if (icon != null) Icon(icon, color, 26)
        BasicText(text, style = AtomixType.small().copy(color = color))
    }
}

/** One quiet line of remote hints, e.g. "◀ ▶ Move · OK Place · ▼ Unpin · Back Cancel". */
@Composable
fun HintBar(text: String, modifier: Modifier = Modifier) {
    GlassPanel(null, modifier, RoundedCornerShape(50)) {
        BasicText(text, Modifier.padding(horizontal = px(28), vertical = px(12)), style = AtomixType.small().copy(color = AtomixColors.TextPrimary))
    }
}

/** A calm message with up to two actions, used for empty, offline and error states. */
@Composable
fun EmptyState(title: String, message: String?, actions: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier, first: Modifier = Modifier) {
    Column(modifier.widthIn(max = px(900)), verticalArrangement = Arrangement.spacedBy(px(16))) {
        BasicText(title, style = AtomixType.rowHeader())
        if (message != null) BasicText(message, style = AtomixType.small(), modifier = Modifier.widthIn(max = px(820)))
        if (actions.isNotEmpty()) Row(Modifier.padding(top = px(8)), horizontalArrangement = Arrangement.spacedBy(px(20))) {
            actions.forEachIndexed { i, (label, action) -> ChipButton(label, action, if (i == 0) first else Modifier) }
        }
    }
}

/** Red LIVE badge (#C62F3A, white text 5.4:1). */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    BasicText(
        "LIVE",
        modifier.background(AtomixColors.Live, RoundedCornerShape(px(8))).padding(horizontal = px(12), vertical = px(4)),
        style = AtomixType.badge().copy(textAlign = TextAlign.Center),
    )
}
