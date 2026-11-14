package dev.atomix.launcher.ui.kit

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

enum class Tone { Good, Warn, Plain }

/** What sits at the right end of a [SettingRow]. */
sealed interface RowEnding {
    data object None : RowEnding
    data class Value(val text: String, val chevron: Boolean = true) : RowEnding
    data class Switch(val on: Boolean) : RowEnding
    data class Pills(val options: List<String>, val selected: Int) : RowEnding
    /** Colour dots (opaque ARGB), the chosen one larger, ringed and ticked. Left and Right step it, like [Pills]. */
    data class Dots(val colours: List<Long>, val selected: Int) : RowEnding
    data class Status(val text: String, val tone: Tone, val chevron: Boolean = false) : RowEnding
}

/**
 * One row of a settings list (92 high): a label, an optional one-line description, and an ending. OK calls [onClick];
 * with [RowEnding.Pills] or [RowEnding.Dots] Left and Right choose an option through [onPill]. A null [onClick] makes
 * the row a plain statement that cannot take focus. [descriptionColor] is for a description that is a warning. [held]
 * is for a row whose list of choices is open: the one ring is in the list, and the row keeps a quieter outline.
 */
@Composable
fun SettingRow(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: LineIcon? = null,
    ending: RowEnding = RowEnding.None,
    onPill: (Int) -> Unit = {},
    onFocused: () -> Unit = {},
    descriptionColor: Color = Color.Unspecified,
    held: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(px(18))
    var row = modifier
        .onFocusChanged { if (it.isFocused) onFocused() }
        .tvFocus(focused, cornerPx = 18f, glow = FocusGlow.Row)
        .fillMaxWidth()
        .height(px(92))
        .background(if (focused || held) AtomixColors.Raised else AtomixColors.Tile, shape)
        .then(if (held && !focused) Modifier.border(px(3), AtomixColors.Ring.copy(alpha = 0.60f), shape) else Modifier)
    // How many options Left and Right step through, and which is chosen; null for a row they do not change.
    val stepped: Pair<Int, Int>? = when (ending) {
        is RowEnding.Pills -> ending.options.size to ending.selected
        is RowEnding.Dots -> ending.colours.size to ending.selected
        else -> null
    }
    if (onClick != null) {
        if (stepped != null) row = row.onPreviewKeyEvent { e ->
            val step = when (e.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> return@onPreviewKeyEvent false
            }
            val to = stepped.second + step
            // Left on the first option is left alone, so focus can go back to whatever is beside the rows.
            if (to < 0) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown && to < stepped.first) onPill(to)
            true
        }
        row = row.clickable(interaction, indication = null, onClick = onClick)
    }
    Row(row.padding(horizontal = px(28)), horizontalArrangement = Arrangement.spacedBy(px(24)), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, AtomixColors.Ice, 30)
        Column(Modifier.weight(1f)) {
            BasicText(label, style = AtomixType.rowLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description != null) {
                val style = AtomixType.description()
                BasicText(description, style = if (descriptionColor == Color.Unspecified) style else style.copy(color = descriptionColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (ending) {
            RowEnding.None -> Unit
            is RowEnding.Value -> {
                // An empty value leaves just the chevron: a row that goes somewhere.
                if (ending.text.isNotEmpty()) BasicText(ending.text, style = AtomixType.value(), maxLines = 1)
                if (ending.chevron) Icon(LineIcon.ChevronRight, AtomixColors.TextSecondary, 26)
            }
            is RowEnding.Switch -> {
                BasicText(if (ending.on) "On" else "Off", style = AtomixType.hint())
                SwitchTrack(ending.on)
            }
            is RowEnding.Pills -> PillTrack(ending.options, ending.selected)
            is RowEnding.Dots -> DotTrack(ending.colours, ending.selected, surface = if (focused || held) AtomixColors.Raised else AtomixColors.Tile)
            is RowEnding.Status -> {
                StatusChip(ending.text, ending.tone)
                if (ending.chevron) Icon(LineIcon.ChevronRight, AtomixColors.TextSecondary, 26)
            }
        }
    }
}

@Composable
private fun SwitchTrack(on: Boolean) {
    Box(
        Modifier.size(px(76), px(42)).background(if (on) AtomixColors.Ice else AtomixColors.SwitchOff, RoundedCornerShape(px(21))).padding(px(4)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(px(34)).background(if (on) AtomixColors.Ink else AtomixColors.Label, CircleShape))
    }
}

/**
 * Colour dots, 20 apart: 44 across, and the chosen one 52 with a dark tick, inside a white ring that stands 3 clear of
 * it (the row's own [surface] shows in between), as on the Appearance board.
 */
@Composable
private fun DotTrack(colours: List<Long>, selected: Int, surface: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(px(20)), verticalAlignment = Alignment.CenterVertically) {
        colours.forEachIndexed { i, argb ->
            if (i == selected) {
                Box(Modifier.size(px(64)).background(Color.White, CircleShape).padding(px(3)).background(surface, CircleShape).padding(px(3)).background(Color(argb), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(LineIcon.Tick, AtomixColors.Ink, 26)
                }
            } else {
                Box(Modifier.size(px(44)).background(Color(argb), CircleShape))
            }
        }
    }
}

/** A small coloured chip that states a fact: green with a tick, amber with a warning mark, or plain. */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (ground, ink, icon) = when (tone) {
        Tone.Good -> Triple(AtomixColors.Green, AtomixColors.GreenText, LineIcon.Tick)
        Tone.Warn -> Triple(AtomixColors.Amber, AtomixColors.Amber, LineIcon.Warn)
        Tone.Plain -> Triple(AtomixColors.TextSoft, AtomixColors.TextSoft, null)
    }
    Row(
        modifier.height(px(44)).background(ground.copy(alpha = 0.16f), RoundedCornerShape(px(12))).padding(horizontal = px(16)),
        horizontalArrangement = Arrangement.spacedBy(px(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, ink, 22)
        BasicText(text, style = AtomixType.hint().copy(fontWeight = FontWeight.Bold, color = ink), maxLines = 1)
    }
}

/** The segmented track as a picture of the choice, for the end of a row; the row itself takes the keys. */
@Composable
private fun PillTrack(options: List<String>, selected: Int) {
    Row(Modifier.height(px(56)).background(AtomixColors.Inset, RoundedCornerShape(px(16))).padding(px(4))) {
        options.forEachIndexed { i, option -> Pill(option, i == selected, focused = false, Modifier) }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, focused: Boolean, modifier: Modifier, padPx: Int = 20) {
    // The chosen pill is ice, and so is the focus ring. With focus on it, the ice draws 4 px in from the ring, so the
    // ring still reads as a ring (and does without its glow, with Reduced effects on).
    val gap = if (selected && focused) 4 else 0
    Box(
        modifier.height(px(48)).background(if (focused) AtomixColors.Raised else Color.Transparent, RoundedCornerShape(px(12))).padding(px(gap)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxHeight().background(if (selected) AtomixColors.Ice else Color.Transparent, RoundedCornerShape(px(12 - gap))).padding(horizontal = px(padPx - gap)),
            contentAlignment = Alignment.Center,
        ) {
            val style = if (selected) AtomixType.hint().copy(fontWeight = FontWeight.Bold, color = AtomixColors.Ink)
            else AtomixType.hint().copy(color = if (focused) AtomixColors.Text else AtomixColors.TextSecondary)
            BasicText(text, style = style, maxLines = 1)
        }
    }
}

/**
 * A segmented choice that takes focus pill by pill; OK on a pill selects it. [selectedFocus], when given, is attached
 * to the chosen pill, so a screen can send focus to it. The track's measurements are the
 * settings boards'; the channel list over video draws a slightly roomier one.
 */
@Composable
fun SegmentedPills(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Int) -> Unit = {},
    trackPx: Int = 56,
    padPx: Int = 4,
    pillPadPx: Int = 20,
    gapPx: Int = 0,
    selectedFocus: FocusRequester? = null,
) {
    Row(
        modifier.height(px(trackPx)).background(AtomixColors.Inset, RoundedCornerShape(px(16))).padding(px(padPx)),
        horizontalArrangement = Arrangement.spacedBy(px(gapPx)),
    ) {
        options.forEachIndexed { i, option ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Pill(
                option, i == selected, focused,
                (if (i == selected && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocused(i) }
                    .tvFocus(focused, cornerPx = 12f)
                    .clickable(interaction, indication = null) { onSelect(i) },
                pillPadPx,
            )
        }
    }
}
