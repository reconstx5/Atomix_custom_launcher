package dev.atomix.launcher.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Orbit tokens, from Atomix DESIGN.md. */
object AtomixColors {
    val DeepSpace = Color(0xFF05050C)
    val DeepSpaceInset = Color(0xFF0B0B16)
    val Ice = Color(0xFF8FE9FF)
    val Text = Color.White
    val TextSoft = Color.White.copy(alpha = 0.78f)
    val TextDim = Color.White.copy(alpha = 0.55f)
    val GlassClear = Color.White.copy(alpha = 0.14f)
    val GlassStrong = Color.White.copy(alpha = 0.22f)
    val GlassEdge = Color.White.copy(alpha = 0.16f)
    val GlassPanel = Color(0xCC0B0B16)
    val Coral = Color(0xFFFF7A86)
    // Overhaul tokens (research: contrast checked against the brightest artwork).
    val TextPrimary = Color(0xFFF1F1F1)
    val TextSecondary = Color(0xFFB4B8BF)
    val Live = Color(0xFFC62F3A)
    val SolidPanel = Color(0xFF141726)
    val Edge = Color.White.copy(alpha = 0.16f)
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
    @Composable fun display() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = pxSp(116), lineHeight = pxSp(125), letterSpacing = (-0.045).em(), color = AtomixColors.Text)
    @Composable fun headline() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = pxSp(56), letterSpacing = (-0.035).em(), color = AtomixColors.Text)
    @Composable fun title() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(26), letterSpacing = (-0.015).em(), color = AtomixColors.Text)
    @Composable fun body() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = pxSp(22), lineHeight = pxSp(33), color = AtomixColors.TextSoft)
    @Composable fun label() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(20), color = AtomixColors.TextSoft)
    // Overhaul scale (px at 1920×1080; floor 22).
    @Composable fun spotlight() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(80), lineHeight = pxSp(88), color = AtomixColors.TextPrimary)
    @Composable fun eyebrow() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = pxSp(24), lineHeight = pxSp(32), letterSpacing = 0.16.em(), color = AtomixColors.TextSecondary)
    @Composable fun meta() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = pxSp(30), lineHeight = pxSp(38), color = AtomixColors.TextSecondary)
    @Composable fun rowHeader() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = pxSp(30), lineHeight = pxSp(38), color = AtomixColors.TextPrimary)
    @Composable fun menu() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = pxSp(28), lineHeight = pxSp(36), color = AtomixColors.TextPrimary)
    @Composable fun cardTitle() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = pxSp(28), lineHeight = pxSp(34), color = AtomixColors.TextPrimary)
    @Composable fun small() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = pxSp(24), lineHeight = pxSp(30), color = AtomixColors.TextSecondary)
    @Composable fun badge() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = pxSp(22), lineHeight = pxSp(28), color = Color.White)
    @Composable fun caption() = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = pxSp(16), color = AtomixColors.TextDim)
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

@Composable
fun AtomixTheme(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    CompositionLocalProvider(LocalUnit provides widthDp / 1920f) { content() }
}

/** White ring with a dark gap (Orbit `--ring`: 5 px Deep Space, then 4 px white). */
@Composable
fun Modifier.atomixFocusRing(focused: Boolean, cornerPx: Float): Modifier {
    if (!focused) return this
    val density = LocalDensity.current
    val unit = LocalUnit.current
    val gap = with(density) { (5 * unit).dp.toPx() }
    val ring = with(density) { (4 * unit).dp.toPx() }
    val corner = with(density) { (cornerPx * unit).dp.toPx() }
    return this.drawWithContent {
        drawContent()
        val g = gap / 2
        drawRoundRect(AtomixColors.DeepSpace, Offset(-g, -g), Size(size.width + gap, size.height + gap), CornerRadius(corner + g), style = Stroke(gap))
        val r = gap + ring / 2
        drawRoundRect(Color.White, Offset(-r, -r), Size(size.width + 2 * r, size.height + 2 * r), CornerRadius(corner + r), style = Stroke(ring))
    }
}

/** Orbit pill button: glass at rest, solid white with ink text and 1.05 scale when focused. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    leading: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(180), label = "btn")
    val shape = RoundedCornerShape(50)
    val bg = when { focused -> Color.White; primary -> AtomixColors.GlassStrong; else -> AtomixColors.GlassClear }
    Box(
        modifier
            .scale(scale)
            .atomixFocusRing(focused, 999f)
            .background(bg, shape)
            .border(1.dp, if (focused) Color.Transparent else AtomixColors.GlassEdge, shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .heightIn(min = px(66))
            .padding(start = px(28), end = px(34)),
        contentAlignment = Alignment.Center,
    ) {
        val style = AtomixType.body().copy(fontWeight = FontWeight.SemiBold, color = if (focused) AtomixColors.DeepSpaceInset else Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(px(12)), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) BasicText(leading, style = style)
            BasicText(text, style = style)
        }
    }
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
            style = AtomixType.title().copy(fontSize = pxSp(sizePx * 0.42f), fontWeight = FontWeight.Bold),
        )
    }
}
