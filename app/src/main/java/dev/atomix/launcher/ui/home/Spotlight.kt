package dev.atomix.launcher.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import coil.transform.Transformation
import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.OnAir
import dev.atomix.launcher.ui.kit.Backdrop
import dev.atomix.launcher.ui.kit.LiveBadge
import dev.atomix.launcher.ui.kit.LocalEffects
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ART_W = 1280
private const val ART_H = 720

/** Deep Space with alpha, as an ARGB int. */
private fun shade(alpha: Float): Int = ((alpha * 255).toInt() shl 24) or 0x05050C

/**
 * Centre-crops programme art to 16:9 and bakes the page shade into it (left for the text, bottom for the rows, top
 * for the menu) once per decode, so each frame draws one bitmap and no gradients (12–15 ms a frame on this TV).
 */
class SpotlightScrim : Transformation {
    override val cacheKey: String = "spotlight-scrim-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val out = Bitmap.createBitmap(ART_W, ART_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val srcAspect = input.width.toFloat() / input.height
        val dstAspect = ART_W.toFloat() / ART_H
        val src = if (srcAspect > dstAspect) {
            val w = (input.height * dstAspect).toInt()
            Rect((input.width - w) / 2, 0, (input.width + w) / 2, input.height)
        } else {
            val h = (input.width / dstAspect).toInt()
            Rect(0, (input.height - h) / 2, input.width, (input.height + h) / 2)
        }
        canvas.drawBitmap(input, src, Rect(0, 0, ART_W, ART_H), Paint(Paint.FILTER_BITMAP_FLAG))
        val w = ART_W.toFloat(); val h = ART_H.toFloat()
        val paint = Paint()
        paint.shader = LinearGradient(0f, 0f, w * 0.65f, 0f, shade(0.92f), shade(0f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w * 0.65f, h, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.2f, shade(0.7f), shade(0f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.2f, paint)
        paint.shader = LinearGradient(0f, h * 0.45f, 0f, h, shade(0f), shade(0.97f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.45f, w, h, paint)
        return out
    }
}

private val scrim = SpotlightScrim()

/** The shaded art plus its blurred copy (for glass) and a darker copy (while focus is in the apps row). */
@Immutable
class SpotlightArt(val url: String, val art: ImageBitmap, val blurred: ImageBitmap, val dimmed: ImageBitmap)

/** Loads [url] (null = Deep Space) and prepares the glass copies off the main thread. */
@Composable
fun rememberSpotlightArt(url: String?, loader: ImageLoader): SpotlightArt? {
    val context = LocalContext.current
    var art by remember { mutableStateOf<SpotlightArt?>(null) }
    LaunchedEffect(url) {
        if (url == null) { art = null; return@LaunchedEffect }
        val req = ImageRequest.Builder(context).data(url).size(ART_W, ART_H).allowHardware(false).transformations(scrim).build()
        val bmp = ((loader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        art = bmp?.let {
            withContext(Dispatchers.Default) {
                SpotlightArt(url, it.asImageBitmap(), Backdrop.blurredCopy(it).asImageBitmap(), Backdrop.dimmed(it, 0.55f).asImageBitmap())
            }
        }
    }
    return art
}

/** Full-bleed backdrop: the art (or its darker copy), crossfading on change; Deep Space when there's none. */
@Composable
fun SpotlightBackdrop(art: SpotlightArt?, dim: Boolean, modifier: Modifier = Modifier) {
    val ms = LocalEffects.current.crossfadeMs
    Box(modifier.fillMaxSize().background(AtomixColors.DeepSpace)) {
        Crossfade(art to dim, animationSpec = tween(ms), label = "backdrop") { (a, d) ->
            if (a != null) {
                Image(if (d) a.dimmed else a.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(AtomixColors.DeepSpaceInset, AtomixColors.DeepSpace))))
            }
        }
    }
}

/**
 * The focused channel's identity and programme, bottom-anchored over the backdrop: eyebrow (number · channel),
 * title, times/genre/minutes left and a one-line description. No buttons — OK on the card plays.
 */
@Composable
fun Spotlight(channel: Channel?, on: OnAir?, logoUrl: String?, loader: ImageLoader, is24: Boolean, modifier: Modifier = Modifier) {
    if (channel == null) return
    Column(modifier.widthIn(max = px(1000)), verticalArrangement = Arrangement.spacedBy(px(14))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(16))) {
            LogoPlate(channel, logoUrl, loader, Modifier.height(px(56)), logoMax = 110 to 36)
            BasicText(listOfNotNull(channel.number?.toString(), channel.name).joinToString(" · ").uppercase(), style = AtomixType.eyebrow())
            LiveBadge()
        }
        val p = on?.programme
        BasicText(p?.title ?: channel.name, style = AtomixType.spotlight(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        val meta = if (p == null) "No programme information"
        else listOfNotNull(timeRange(p.start, p.end, is24), p.categories.firstOrNull(), "${on.minutesLeft} min left").joinToString(" · ")
        BasicText(meta, style = AtomixType.meta())
        p?.description?.takeIf { it.isNotBlank() }?.let {
            BasicText(it, style = AtomixType.meta().copy(color = AtomixColors.TextPrimary.copy(alpha = 0.85f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
