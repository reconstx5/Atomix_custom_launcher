package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

/** A 64-high heading row: the title on the left and, when given, one quiet action at the right end. */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().height(px(64)), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, style = AtomixType.heading(), maxLines = 1)
        Spacer(Modifier.weight(1f))
        // The action carries 16 px of padding for its focused surface; shifted so its words still end on the margin.
        if (action != null) Box(Modifier.offset(x = px(16))) { action() }
    }
}

/** A quiet text action such as "Edit": an icon and a word, white on a raised surface with the ring when focused. */
@Composable
fun TextAction(text: String, icon: LineIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(14))
    val color = if (focused) AtomixColors.Text else AtomixColors.TextSecondary
    Row(
        modifier
            .tvFocus(focused, cornerPx = 14f)
            .background(if (focused) AtomixColors.Raised else Color.Transparent, shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = px(16), vertical = px(8)),
        horizontalArrangement = Arrangement.spacedBy(px(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, color, 28)
        BasicText(text, style = AtomixType.subheading().copy(fontWeight = FontWeight.Normal, lineHeight = pxSp(38), color = color), maxLines = 1)
    }
}

/**
 * A full page under the top bar: the heading at y 126, the content from y 210 down to the hint bar, both inside the
 * 100 px margins, and the hint bar bottom right. While focus is outside the page (it is in the top bar) the hints say
 * what the top bar does and where Back goes ([barBack]). [more] is the "more below" line, bottom left.
 */
@Composable
fun ScreenFrame(
    heading: String,
    hints: List<Hint>,
    barBack: String = "Home",
    action: (@Composable () -> Unit)? = null,
    more: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var inside by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().onFocusChanged { inside = it.hasFocus }) {
            SectionHeading(heading, Modifier.padding(start = px(100), end = px(100), top = px(126)), action)
            Box(Modifier.fillMaxSize().padding(start = px(100), end = px(100), top = px(210), bottom = px(100)), content = content)
        }
        if (more != null) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = px(100), bottom = px(40)).height(px(60)),
                horizontalArrangement = Arrangement.spacedBy(px(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LineIcon.ChevronDown, AtomixColors.TextSecondary, 24)
                BasicText(more, style = AtomixType.description(), maxLines = 1)
            }
        }
        ScreenHints(if (inside) hints else hintsFor(HintSpot.TopBar(barBack)))
    }
}
