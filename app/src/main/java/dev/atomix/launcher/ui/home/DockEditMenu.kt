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
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.GlassButton
import dev.atomix.launcher.ui.theme.px

data class MenuOption(val label: String, val onClick: () -> Unit)

/** A small centred glass menu; used for dock editing and the apps grid. */
@Composable
fun GlassMenu(title: String, options: List<MenuOption>, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(AtomixColors.DeepSpace.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(px(28))
        Column(
            Modifier.width(px(560)).background(AtomixColors.GlassPanel, shape).border(1.dp, AtomixColors.GlassEdge, shape).padding(px(40)),
            verticalArrangement = Arrangement.spacedBy(px(16)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(title, style = AtomixType.title(), maxLines = 1)
            (options + MenuOption("Cancel") {}).forEachIndexed { i, o ->
                GlassButton(
                    o.label,
                    onClick = { onDismiss(); o.onClick() },
                    modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).width(px(400)),
                )
            }
        }
    }
}
