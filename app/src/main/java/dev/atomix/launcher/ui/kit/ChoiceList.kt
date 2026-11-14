@file:OptIn(ExperimentalComposeUiApi::class) // FocusRequester.Cancel

package dev.atomix.launcher.ui.kit

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** The width of a menu panel, px at 1920: the manage-apps menu and a settings row's list of choices. */
const val MENU_W = 470

/**
 * A setting's choices as a short list: its name, then one row per choice, the chosen one ticked. [chosenFocus] is
 * attached to the chosen row, which is where focus goes when the list opens. OK on a row chooses it; focus cannot
 * leave the list by the arrows, so the only ways out are OK and Back, which the screen that opened it handles.
 */
@Composable
fun ChoiceList(
    title: String,
    options: List<String>,
    selected: Int,
    chosenFocus: FocusRequester,
    onChoose: (Int) -> Unit,
    onFocused: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(px(22))
    Column(
        modifier.width(px(MENU_W)).background(AtomixColors.Panel, shape).border(px(2), AtomixColors.Ice.copy(alpha = 0.24f), shape).padding(px(20)),
        verticalArrangement = Arrangement.spacedBy(px(10)),
    ) {
        BasicText(title, style = AtomixType.rowLabel().copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        options.forEachIndexed { i, option ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Row(
                (if (i == selected) Modifier.focusRequester(chosenFocus) else Modifier)
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        if (i == 0) up = FocusRequester.Cancel
                        if (i == options.lastIndex) down = FocusRequester.Cancel
                    }
                    .onFocusChanged { if (it.isFocused) onFocused(i) }
                    .tvFocus(focused, cornerPx = 16f)
                    .fillMaxWidth()
                    .height(px(72))
                    .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, RoundedCornerShape(px(16)))
                    .clickable(interaction, indication = null) { onChoose(i) }
                    .padding(horizontal = px(20)),
                horizontalArrangement = Arrangement.spacedBy(px(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    option,
                    Modifier.weight(1f),
                    style = AtomixType.button().copy(fontWeight = if (focused || i == selected) FontWeight.Bold else FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (i == selected) Icon(LineIcon.Tick, AtomixColors.Ice, 28)
            }
        }
    }
}
