package dev.atomix.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.atomix.launcher.apps.InputEntry
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.GlassButton
import dev.atomix.launcher.ui.theme.px

@Composable
fun InputsDialog(inputs: List<InputEntry>, onPick: (InputEntry) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val first = remember { FocusRequester() }
    LaunchedEffect(inputs) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(AtomixColors.DeepSpace.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(px(28))
        Column(
            Modifier.width(px(560)).background(AtomixColors.GlassPanel, shape).border(1.dp, AtomixColors.GlassEdge, shape).padding(px(40)),
            verticalArrangement = Arrangement.spacedBy(px(16)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText("Inputs", style = AtomixType.headline())
            if (inputs.isEmpty()) BasicText("No HDMI inputs found.", style = AtomixType.body())
            inputs.forEachIndexed { i, input ->
                GlassButton(
                    input.label,
                    onClick = { onPick(input) },
                    modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).width(px(400)),
                )
            }
            GlassButton("Close", onClick = onDismiss, modifier = if (inputs.isEmpty()) Modifier.focusRequester(first) else Modifier)
        }
    }
}
