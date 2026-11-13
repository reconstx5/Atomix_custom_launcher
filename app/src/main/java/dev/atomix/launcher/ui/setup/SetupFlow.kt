package dev.atomix.launcher.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import dev.atomix.launcher.data.Profile
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.Avatar
import dev.atomix.launcher.ui.theme.GlassButton
import dev.atomix.launcher.ui.theme.atomixFocusRing
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp

@Composable
fun SetupFlow(vm: SetupViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state) { if (state == SetupState.Done) onDone() }
    Box(Modifier.fillMaxSize().background(AtomixColors.DeepSpace).padding(px(96)), contentAlignment = Alignment.Center) {
        when (val s = state) {
            SetupState.FindingServer -> Message("Looking for Atomix on your network…")
            is SetupState.EnterAddress -> AddressScreen(s.error, vm::submitAddress, vm::start)
            is SetupState.QuickConnect -> QuickConnectScreen(s)
            is SetupState.ChooseProfile -> WhoIsWatching(s.profiles, vm::choose)
            is SetupState.EnterPin -> PinScreen(s.profile, s.error, vm::submitPin, vm::backToProfiles)
            SetupState.Done -> Message("Opening Atomix…")
        }
    }
}

@Composable private fun Message(text: String) = BasicText(text, style = AtomixType.title())

@Composable
private fun AddressScreen(error: String?, submit: (String) -> Unit, retry: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(px(28))) {
        BasicText("Where is Atomix?", style = AtomixType.headline())
        BasicText(
            "Atomix wasn't found on this network. Type the address of the computer running it, for example 192.168.8.65. If the computer uses a VPN, allow local network access.",
            style = AtomixType.body().copy(textAlign = TextAlign.Center),
            modifier = Modifier.width(px(1100)),
        )
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            singleLine = true,
            interactionSource = interaction,
            textStyle = AtomixType.title(),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit(text) }),
            modifier = Modifier
                .focusRequester(field)
                .atomixFocusRing(focused, 18f)
                .background(AtomixColors.GlassClear, RoundedCornerShape(px(18)))
                .padding(horizontal = px(28), vertical = px(18))
                .width(px(640)),
        )
        if (error != null) BasicText(error, style = AtomixType.label().copy(color = AtomixColors.Coral))
        Row(horizontalArrangement = Arrangement.spacedBy(px(20))) {
            GlassButton("Connect", onClick = { submit(text) }, primary = true)
            GlassButton("Search again", onClick = retry)
        }
    }
}

@Composable
private fun QuickConnectScreen(s: SetupState.QuickConnect) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(px(30))) {
        BasicText("Sign in to Atomix", style = AtomixType.headline())
        BasicText(
            s.code.toCharArray().joinToString(" "),
            style = AtomixType.display().copy(letterSpacing = pxSp(18), color = Color.White),
        )
        BasicText(
            "On a device that's signed in, open Settings → Profile → Quick Connect and type this code.",
            style = AtomixType.body().copy(textAlign = TextAlign.Center),
            modifier = Modifier.width(px(1000)),
        )
        val minutes = s.secondsLeft / 60
        val seconds = s.secondsLeft % 60
        BasicText("Code changes in %d:%02d · %s".format(minutes, seconds, s.server.trimEnd('/')), style = AtomixType.caption())
    }
}

@Composable
private fun WhoIsWatching(profiles: List<Profile>, choose: (Profile) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(profiles) { runCatching { first.requestFocus() } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(px(56))) {
        BasicText("Who's watching?", style = AtomixType.headline())
        Row(horizontalArrangement = Arrangement.spacedBy(px(48))) {
            profiles.forEachIndexed { i, p ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .clickable(interaction, indication = null) { choose(p) },
                ) {
                    Avatar(
                        p.name, p.avatar, 180,
                        Modifier.scale(if (focused) 1.08f else 1f).atomixFocusRing(focused, 90f),
                    )
                    Spacer(Modifier.height(px(20)))
                    BasicText(
                        p.name + if (p.kids) " · Kids" else "",
                        style = AtomixType.title().copy(color = if (focused) Color.White else AtomixColors.TextSoft),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinScreen(profile: Profile, error: String?, submit: (String) -> Unit, back: () -> Unit) {
    var pin by remember(profile.id, error) { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(profile.id, error) { runCatching { first.requestFocus() } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(px(28))) {
        Avatar(profile.name, profile.avatar, 120)
        BasicText("Enter ${profile.name}'s PIN", style = AtomixType.headline())
        Row(horizontalArrangement = Arrangement.spacedBy(px(18))) {
            repeat(4) { i ->
                Box(
                    Modifier.size(px(22)).background(if (i < pin.length) Color.White else AtomixColors.GlassClear, CircleShape)
                        .border(1.dp(), AtomixColors.GlassEdge, CircleShape),
                )
            }
        }
        if (error != null) BasicText(error, style = AtomixType.label().copy(color = AtomixColors.Coral))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        Column(verticalArrangement = Arrangement.spacedBy(px(14))) {
            keys.chunked(3).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(px(14))) {
                    row.forEachIndexed { c, k ->
                        PinKey(k, if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier) {
                            when (k) {
                                "⌫" -> pin = pin.dropLast(1)
                                "OK" -> if (pin.isNotEmpty()) submit(pin)
                                else -> if (pin.length < 8) { pin += k; if (pin.length == 4) submit(pin) }
                            }
                        }
                    }
                }
            }
        }
        GlassButton("Choose another profile", onClick = back)
    }
}

@Composable
private fun PinKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .atomixFocusRing(focused, 999f)
            .size(px(96))
            .background(if (focused) Color.White else AtomixColors.GlassClear, CircleShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = AtomixType.title().copy(fontWeight = FontWeight.SemiBold, color = if (focused) AtomixColors.DeepSpaceInset else Color.White))
    }
}

@Composable private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
