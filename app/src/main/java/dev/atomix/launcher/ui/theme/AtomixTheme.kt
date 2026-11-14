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

/** The redesign's colours (design spec §4.1). */
object AtomixColors {
    val Ground = Color(0xFF0A1322)
    val Tile = Color(0xFF16243A)
    val Raised = Color(0xFF1B2C46)
    val Panel = Color(0xFF101B2C)
    val Inset = Color(0xFF0E1828)
    val KeyCap = Color(0xFF24344D)
    val RoundButton = Color(0xFF1A2638)
    /** The card colour under artwork. */
    val Card = Color(0xFF111D2E)
    val OverVideo = Color(0xEB0A1220)
    val OverVideoList = Color(0xF00A1220)
    val HintChip = Color(0xD60A1220)
    val Ice = Color(0xFFA9DCFB)
    val Ring = Color(0xFFBFE6FF)
    /** Text and icons on ice. */
    val Ink = Color(0xFF0A1322)
    val Text = Color.White
    val TextBright = Color(0xFFE6ECF3)
    val TextSoft = Color(0xFFD5DDE8)
    val TextSecondary = Color(0xFFC3CEDC)
    val Label = Color(0xFF9FB0C6)
    val Inactive = Color(0xFFA7B2C3)
    val InactiveSoft = Color(0xFF97A3B6)
    val InactiveDim = Color(0xFF8794A8)
    val Green = Color(0xFF7BE0A3)
    val GreenText = Color(0xFF9BEBBB)
    val Amber = Color(0xFFFFD08A)
    val LiveRed = Color(0xFFFF5A5F)
    val LiveText = Color(0xFFFFB3B5)
    val LogoPlate = Color(0xFFF2F5F9)
    val SwitchOff = Color(0xFF2A3A52)
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

@Composable fun pxSp(v: Number): TextUnit {
    val density = LocalDensity.current
    return (v.toFloat() * LocalUnit.current / density.fontScale).sp
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
