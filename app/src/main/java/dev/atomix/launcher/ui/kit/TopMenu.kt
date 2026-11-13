package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

enum class Tab(val label: String) { Home("Home"), LiveTv("Live TV"), Apps("Apps") }

/**
 * Top bar: wordmark left, the glass pill menu (Home · Live TV · Apps) in the centre, and a second glass pill on the
 * right with the clock, Inputs and Settings. [firstFocus] is attached to the selected tab.
 */
@Composable
fun TopMenu(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onInputs: () -> Unit,
    onSettings: () -> Unit,
    clock: String,
    blurred: ImageBitmap?,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(px(64))) {
        BasicText(
            "ATOMIX TV",
            Modifier.align(Alignment.CenterStart),
            style = AtomixType.eyebrow().copy(color = AtomixColors.TextPrimary, fontWeight = FontWeight.SemiBold),
        )
        GlassPanel(blurred, Modifier.align(Alignment.Center), RoundedCornerShape(50)) {
            Row(Modifier.padding(horizontal = px(8)).height(px(64)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(8))) {
                Tab.entries.forEach { tab ->
                    MenuItem(tab.label, tab == selected, { onSelect(tab) }, if (tab == selected) Modifier.focusRequester(firstFocus) else Modifier)
                }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(16))) {
            BasicText(clock, style = AtomixType.menu())
            IconButton(LineIcon.Inputs, "Inputs", onInputs)
            IconButton(LineIcon.Settings, "Settings", onSettings)
        }
    }
}

@Composable
private fun MenuItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Column(
        modifier
            .background(if (focused) Color.White else Color.Transparent, shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .height(px(48))
            .padding(horizontal = px(28)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            label,
            style = AtomixType.menu().copy(
                color = if (focused) AtomixColors.DeepSpace else if (selected) AtomixColors.TextPrimary else AtomixColors.TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
        Box(Modifier.size(px(6)).background(if (selected && !focused) AtomixColors.Ice else Color.Transparent, CircleShape))
    }
}
