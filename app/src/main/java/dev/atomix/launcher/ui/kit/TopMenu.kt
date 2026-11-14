package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.atomix.launcher.shell.TopButton
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

enum class Tab(val label: String) { Home("Home"), LiveTv("Live TV"), Apps("Apps") }

/**
 * Top bar (64 high): the wordmark on the left, the three text tabs in the centre with an ice underline under the open
 * one, and on the right the clock and the round Inputs and Settings buttons. [selected] is the open tab, or null when
 * a round button's screen is open ([current]). [firstFocus] is attached to whichever of those is marked. A null
 * [clock] draws no clock (Settings › Appearance › Clock › Hidden).
 */
@Composable
fun TopMenu(
    selected: Tab?,
    current: TopButton?,
    onSelect: (Tab) -> Unit,
    onInputs: () -> Unit,
    onSettings: () -> Unit,
    clock: String?,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(px(64))) {
        BasicText("ATOMIX TV", Modifier.align(Alignment.CenterStart), style = AtomixType.wordmark(), maxLines = 1)
        // A tab's text sits 64 from its neighbour's; each carries 18 of side padding for its focused surface.
        Row(
            Modifier.align(Alignment.Center).wrapContentHeight(unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(px(64 - 2 * 18)),
        ) {
            Tab.entries.forEach { tab ->
                TabItem(tab.label, tab == selected, { onSelect(tab) }, if (tab == selected) Modifier.focusRequester(firstFocus) else Modifier)
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(24))) {
            if (clock != null) BasicText(clock, style = AtomixType.clock(), maxLines = 1)
            IconButton(
                LineIcon.Inputs, "Inputs", onInputs,
                if (current == TopButton.Inputs) Modifier.focusRequester(firstFocus) else Modifier,
                current = current == TopButton.Inputs,
            )
            IconButton(
                LineIcon.Settings, "Settings", onSettings,
                if (current == TopButton.Settings) Modifier.focusRequester(firstFocus) else Modifier,
                current = current == TopButton.Settings,
            )
        }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(14))
    Column(
        modifier
            .tvFocus(focused, cornerPx = 14f)
            .background(if (focused) AtomixColors.Raised else Color.Transparent, shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = px(18), vertical = px(6))
            .width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(px(10)),
    ) {
        BasicText(
            label,
            style = AtomixType.tab().copy(
                color = if (selected || focused) AtomixColors.Text else AtomixColors.Label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().height(px(5)).background(if (selected) AtomixColors.Ice else Color.Transparent, RoundedCornerShape(px(3))))
    }
}
