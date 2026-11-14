package dev.atomix.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atomix.launcher.R

/**
 * The launcher's colours (design spec §4.1). The ground, the surfaces and the label grey come from the theme that is
 * on, and [Ice] and [Ring] from the accent (phase 2 spec §6): they are read from [LookState] each time, so a screen
 * that uses them redraws when Settings changes the look. The rest are the same in every theme.
 */
object AtomixColors {
    private val theme: Palette get() = LookState.palette
    val Ground: Color get() = Color(theme.ground)
    val Tile: Color get() = Color(theme.tile)
    val Raised: Color get() = Color(theme.raised)
    val Panel: Color get() = Color(theme.panel)
    val Inset: Color get() = Color(theme.inset)
    val KeyCap: Color get() = Color(theme.keyCap)
    val RoundButton: Color get() = Color(theme.roundButton)
    /** The card colour under artwork. */
    val Card: Color get() = Color(theme.card)
    /** The panels over a playing picture: the theme's over-video colour at 92 %, 94 % and 84 %. */
    val OverVideo: Color get() = Color(theme.overVideo).copy(alpha = 0xEB / 255f)
    val OverVideoList: Color get() = Color(theme.overVideo).copy(alpha = 0xF0 / 255f)
    val HintChip: Color get() = Color(theme.overVideo).copy(alpha = 0xD6 / 255f)
    /** The dark colour artwork fades into: the far edge of the ground's wash. */
    val Scrim: Color get() = Color(theme.wash.last())
    /** The accent. The name is the default accent's; with another accent chosen it is that colour. */
    val Ice: Color get() = Color(LookState.look.accent.argb)
    val Ring: Color get() = Color(ringArgb(LookState.look.accent))
    /** Text and icons on ice. */
    val Ink = Color(0xFF0A1322)
    val Text = Color.White
    val TextBright = Color(0xFFE6ECF3)
    val TextSoft = Color(0xFFD5DDE8)
    val TextSecondary = Color(0xFFC3CEDC)
    val Label: Color get() = Color(theme.label)
    val Inactive = Color(0xFFA7B2C3)
    val InactiveSoft = Color(0xFF97A3B6)
    val InactiveDim = Color(0xFF8794A8)
    val Green = Color(0xFF7BE0A3)
    val GreenText = Color(0xFF9BEBBB)
    val Amber = Color(0xFFFFD08A)
    val LiveRed = Color(0xFFFF5A5F)
    val LiveText = Color(0xFFFFB3B5)
    val LogoPlate = Color(0xFFF2F5F9)
    val SwitchOff: Color get() = Color(theme.switchOff)
    val Track = Color.White.copy(alpha = 0.24f)
}

val AVATAR_COLORS = mapOf(
    "ember" to Color(0xFFFF6B3D), "teal" to Color(0xFF14B8A6), "violet" to Color(0xFF8B5CF6), "gold" to Color(0xFFF5B942),
    "rose" to Color(0xFFF43F5E), "sky" to Color(0xFF38BDF8), "lime" to Color(0xFF84CC16), "slate" to Color(0xFF64748B),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun sora(weight: Int) = Font(
    R.font.sora, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Sora = FontFamily(sora(400), sora(500), sora(600), sora(700))

/** Screen width / 1920: every size below is written as px on a 1920-wide screen. */
private val LocalUnit = staticCompositionLocalOf { 0.5f }

@Composable fun px(v: Number): Dp = (v.toFloat() * LocalUnit.current).dp

/** A text size. With "Large" text chosen in Settings every text is 15 % bigger; boxes and positions use [px] and do not move. */
@Composable fun pxSp(v: Number): TextUnit {
    val density = LocalDensity.current
    return (v.toFloat() * LookState.look.textSize.scale * LocalUnit.current / density.fontScale).sp
}

object AtomixType {
    @Composable fun wordmark() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = pxSp(26), letterSpacing = 0.24.em(), color = AtomixColors.Text)
    @Composable fun tab() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = pxSp(32), lineHeight = pxSp(40), color = AtomixColors.Text)
    @Composable fun clock() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = pxSp(36), color = AtomixColors.Text)
    @Composable fun heading() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = pxSp(44), lineHeight = pxSp(56), color = AtomixColors.Text)
    @Composable fun subheading() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(30), lineHeight = pxSp(40), color = AtomixColors.Text)
    @Composable fun rowLabel() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(28), lineHeight = pxSp(36), color = AtomixColors.Text)
    @Composable fun description() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = pxSp(24), lineHeight = pxSp(30), color = AtomixColors.TextSecondary)
    @Composable fun groupLabel() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(22), lineHeight = pxSp(24), letterSpacing = 0.08.em(), color = AtomixColors.Label)
    @Composable fun value() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(26), color = AtomixColors.Ice)
    @Composable fun hint() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(24), color = AtomixColors.Text)
    @Composable fun button() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(26), color = AtomixColors.Text)
    @Composable fun note() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(22), lineHeight = pxSp(28), color = AtomixColors.Text)
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

@Composable
fun AtomixTheme(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    CompositionLocalProvider(LocalUnit provides widthDp / 1920f) { content() }
}

/** Atomix's avatar: the first letter on the profile's colour. */
@Composable
fun Avatar(name: String, color: String?, sizePx: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(px(sizePx)).background(AVATAR_COLORS[color] ?: AVATAR_COLORS.getValue("slate"), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = AtomixType.rowLabel().copy(fontSize = pxSp(sizePx * 0.42f), lineHeight = pxSp(sizePx * 0.5f), fontWeight = FontWeight.Bold),
        )
    }
}
