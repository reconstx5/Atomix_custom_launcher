package dev.atomix.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.ImageLoader
import coil.compose.AsyncImage
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.apps.IconContent
import dev.atomix.launcher.apps.IconQuality
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import dev.atomix.launcher.ui.theme.pxSp
import kotlin.math.roundToInt

/** What an app shows on Home's wide tile: its own icon, its banner, or the first letter of its name. */
enum class HomeArt { Icon, Banner, Letter }

/**
 * Icon first: an app's own icon whenever it is sharp; its banner when the icon is missing or too small and there is
 * one; the letter only when there is neither. A soft icon with no banner beats a letter.
 */
fun homeArtFor(quality: IconQuality, hasBanner: Boolean): HomeArt = when {
    quality == IconQuality.Sharp -> HomeArt.Icon
    hasBanner -> HomeArt.Banner
    quality == IconQuality.Low -> HomeArt.Icon
    else -> HomeArt.Letter
}

data class ArtBox(val w: Int, val h: Int)

/**
 * The artwork's box in a [slot]-high space, px at 1920. An icon or letter is the slot square; a banner is ¾ of the
 * slot high and 16:9 wide, so it covers about the area of the icon and looks as prominent.
 */
fun homeArtBox(art: HomeArt, slot: Int): ArtBox = when (art) {
    HomeArt.Icon, HomeArt.Letter -> ArtBox(slot, slot)
    HomeArt.Banner -> {
        val h = (slot * 3 / 4f).roundToInt()
        ArtBox((h * 16 / 9f).roundToInt(), h)
    }
}

/** How big an icon is drawn ([size] square) and how far it is moved ([dx], [dy]) so its mark fills and centres in the slot. */
data class IconFit(val size: Int, val dx: Int, val dy: Int)

/**
 * An icon whose mark ([content]) covers less than 90 % of it on both sides (empty space built in, like SmartTube's) is
 * drawn larger, until the mark's wider side spans the [slot], and moved so the mark is centred. Never more than half
 * again larger, so a tiny mark is not blown up soft. Its empty edges spill out of the slot; they draw nothing.
 */
fun iconFit(content: IconContent?, slot: Int): IconFit {
    val span = content?.let { maxOf(it.right - it.left, it.bottom - it.top) } ?: 1f
    if (content == null || span >= 0.9f) return IconFit(slot, 0, 0)
    val size = (slot * minOf(1f / span, 1.5f)).roundToInt()
    val dx = ((0.5f - (content.left + content.right) / 2) * size).roundToInt()
    val dy = ((0.5f - (content.top + content.bottom) / 2) * size).roundToInt()
    return IconFit(size, dx, dy)
}

/** The letter fallback: the name's first visible character, upper case; "?" for a blank name. */
fun artLetter(label: String): String {
    val i = label.indexOfFirst { !it.isWhitespace() }
    if (i < 0) return "?"
    return String(Character.toChars(label.codePointAt(i))).uppercase()
}

/**
 * An app's artwork on Home's wide tile, centred in a full-width [slot]-high box. Icons and banners are drawn whole,
 * as the app made them, with nothing behind them; only the letter sits on a plate.
 */
@Composable
fun HomeAppArt(app: AppEntry, loader: ImageLoader, slot: Int, modifier: Modifier = Modifier) {
    val art = homeArtFor(app.iconQuality, app.banner != null)
    val box = homeArtBox(art, slot)
    Box(modifier.fillMaxWidth().height(px(slot)), contentAlignment = Alignment.Center) {
        when (art) {
            HomeArt.Icon -> {
                val fit = iconFit(app.iconContent, box.w)
                AsyncImage(
                    model = app.icon, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.offset(px(fit.dx), px(fit.dy)).requiredSize(px(fit.size)),
                )
            }
            HomeArt.Banner -> AsyncImage(
                model = app.banner, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(px(box.w), px(box.h)).clip(RoundedCornerShape(px(10))),
            )
            HomeArt.Letter -> Box(
                Modifier.size(px(box.w), px(box.h)).background(AtomixColors.Inset, RoundedCornerShape(px(18))),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    artLetter(app.label),
                    style = AtomixType.rowLabel().copy(fontSize = pxSp(slot / 2), lineHeight = pxSp(slot / 2), fontWeight = FontWeight.Bold, color = AtomixColors.Text),
                    maxLines = 1,
                )
            }
        }
    }
}
