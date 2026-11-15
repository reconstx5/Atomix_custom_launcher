package dev.atomix.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import dev.atomix.launcher.prefs.MAX_NAME
import dev.atomix.launcher.ui.kit.ChoiceList
import dev.atomix.launcher.ui.kit.MENU_W
import dev.atomix.launcher.ui.kit.textRoom
import dev.atomix.launcher.ui.kit.tvFocus
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.LookState
import dev.atomix.launcher.ui.theme.px

/** The names offered for a row, in order (spec §7.4). */
val PRESET_NAMES = listOf("Streaming", "Tools", "Games", "Music", "Kids", "News", "Sport")

/** The last choice: a name of one's own, typed on the TV's keyboard. */
const val TYPE_A_NAME = "Type a name…"

/** A typed name as a row keeps it: no spaces at the ends, one between words, at most [MAX_NAME] long; null when blank. */
fun cleanName(typed: String): String? {
    val name = typed.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trimEnd()
    return name.ifEmpty { null }
}

/**
 * Choosing a row's name, as a short list in the look of phase 2's choice lists: the preset names, then
 * [TYPE_A_NAME], which turns the list into a line to type in with the TV's keyboard open. OK on a name, or Done on
 * the keyboard with something typed, gives it to [onName]; Back closes with nothing changed.
 */
@Composable
fun NameChooser(title: String, current: String?, onName: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var typing by remember { mutableStateOf(false) }
    val listFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler { keyboard?.hide(); onClose() }
    if (!typing) {
        val chosen = PRESET_NAMES.indexOf(current)
        ChoiceList(
            title, PRESET_NAMES + TYPE_A_NAME, chosen, listFocus,
            onChoose = { i -> if (i == PRESET_NAMES.size) typing = true else onName(PRESET_NAMES[i]) },
            onFocused = {},
            modifier = modifier,
            focusAt = chosen.coerceAtLeast(0),
        )
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { listFocus.requestFocus() } }
    } else {
        // Starts with the row's own name when it has one that is not a preset, the cursor at its end.
        val start = current?.takeIf { it !in PRESET_NAMES }.orEmpty()
        var text by remember { mutableStateOf(TextFieldValue(start, TextRange(start.length))) }
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val shape = RoundedCornerShape(px(22))
        Column(
            modifier.width(px(textRoom(MENU_W, LookState.look.textSize.scale))).background(AtomixColors.Panel, shape)
                .border(px(2), AtomixColors.Ice.copy(alpha = 0.24f), shape).padding(px(20)),
            verticalArrangement = Arrangement.spacedBy(px(10)),
        ) {
            BasicText(title, style = AtomixType.rowLabel().copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = text,
                onValueChange = { if (it.text.length <= MAX_NAME * 2) text = it },
                modifier = Modifier.focusRequester(fieldFocus),
                singleLine = true,
                textStyle = AtomixType.button().copy(color = AtomixColors.Text, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(AtomixColors.Ice),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Done with nothing typed leaves the line open.
                keyboardActions = KeyboardActions(onDone = { cleanName(text.text)?.let { keyboard?.hide(); onName(it) } }),
                interactionSource = interaction,
                decorationBox = { field ->
                    Box(
                        Modifier.tvFocus(focused, cornerPx = 16f).fillMaxWidth().height(px(72))
                            .background(if (focused) AtomixColors.Raised else AtomixColors.Tile, RoundedCornerShape(px(16)))
                            .padding(horizontal = px(20)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.text.isEmpty()) BasicText("Name", style = AtomixType.button().copy(color = AtomixColors.Label))
                        field()
                    }
                },
            )
        }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { fieldFocus.requestFocus() }; keyboard?.show() }
    }
}
