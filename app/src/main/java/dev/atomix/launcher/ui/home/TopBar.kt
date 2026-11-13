package dev.atomix.launcher.ui.home

import dev.atomix.launcher.ui.kit.Icon
import dev.atomix.launcher.ui.kit.LineIcon
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.Avatar
import dev.atomix.launcher.ui.theme.atomixFocusRing
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun TopBar(profileName: String?, onProfile: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    Row(
        modifier.fillMaxWidth().padding(start = px(64), end = px(64), top = px(44)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LineIcon.Atom, AtomixColors.Ice, 40)
        BasicText(
            "Atomix",
            style = AtomixType.title().copy(fontSize = pxSp(30), fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = px(14)),
        )
        Box(Modifier.weight(1f))
        BasicText(DateFormat.getTimeFormat(context).format(now), style = AtomixType.title().copy(fontWeight = FontWeight.Medium))
        if (profileName != null) {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .padding(start = px(28))
                    .scale(if (focused) 1.05f else 1f)
                    .atomixFocusRing(focused, 999f)
                    .background(if (focused) Color.White else AtomixColors.GlassPanel, shape)
                    .border(1.dp, if (focused) Color.Transparent else AtomixColors.GlassEdge, shape)
                    .clickable(interaction, indication = null, onClick = onProfile)
                    .padding(start = px(8), end = px(20), top = px(8), bottom = px(8)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(px(12)),
            ) {
                Avatar(profileName, null, 40)
                BasicText(profileName, style = AtomixType.label().copy(color = if (focused) AtomixColors.DeepSpaceInset else Color.White))
            }
        }
    }
}
