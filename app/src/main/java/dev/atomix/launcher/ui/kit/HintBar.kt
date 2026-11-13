package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** The remote hints as key-cap chips, e.g. [OK] Watch TVNZ 1 · [Back] Home. Draws nothing for an empty list. */
@Composable
fun HintBar(hints: List<Hint>, modifier: Modifier = Modifier) {
    if (hints.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(px(16))) {
        hints.forEach { HintChip(it) }
    }
}

@Composable
private fun HintChip(hint: Hint) {
    Row(
        Modifier.height(px(60)).background(AtomixColors.HintChip, RoundedCornerShape(px(16))).padding(horizontal = px(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.height(px(44)).background(AtomixColors.KeyCap, RoundedCornerShape(px(12))).padding(horizontal = px(14)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(hint.key, style = AtomixType.hint().copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
        BasicText(hint.label, Modifier.padding(horizontal = px(16)), style = AtomixType.hint(), maxLines = 1)
    }
}

/** The hint bar in its place on a full screen: bottom right, 100 from the edge and 40 from the bottom. */
@Composable
fun BoxScope.ScreenHints(hints: List<Hint>) {
    HintBar(hints, Modifier.align(Alignment.BottomEnd).padding(end = px(100), bottom = px(40)))
}
