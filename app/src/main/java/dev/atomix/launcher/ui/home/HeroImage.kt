package dev.atomix.launcher.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation

const val HERO_W = 1280
const val HERO_H = 720

/** Deep Space with alpha, as an ARGB int. */
private fun shade(alpha: Float): Int = ((alpha * 255).toInt() shl 24) or 0x05050C

/**
 * Centre-crops the backdrop to 16:9 and paints Orbit's page shade into it (strong at the left and bottom,
 * light at the top for the clock and profile pill). Done once per decode, so each frame on the TV draws a
 * single full-screen bitmap instead of a bitmap plus three gradient layers.
 */
class OrbitScrim : Transformation {
    override val cacheKey: String = "orbit-scrim-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val out = Bitmap.createBitmap(HERO_W, HERO_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val srcAspect = input.width.toFloat() / input.height
        val dstAspect = HERO_W.toFloat() / HERO_H
        val src = if (srcAspect > dstAspect) {
            val w = (input.height * dstAspect).toInt()
            Rect((input.width - w) / 2, 0, (input.width + w) / 2, input.height)
        } else {
            val h = (input.width / dstAspect).toInt()
            Rect(0, (input.height - h) / 2, input.width, (input.height + h) / 2)
        }
        canvas.drawBitmap(input, src, Rect(0, 0, HERO_W, HERO_H), Paint(Paint.FILTER_BITMAP_FLAG))
        val w = HERO_W.toFloat()
        val h = HERO_H.toFloat()
        val paint = Paint()
        paint.shader = LinearGradient(0f, 0f, w * 0.6f, 0f, shade(0.92f), shade(0f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w * 0.6f, h, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.16f, shade(0.6f), shade(0f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.16f, paint)
        paint.shader = LinearGradient(0f, h * 0.55f, 0f, h, shade(0f), shade(0.95f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.55f, w, h, paint)
        return out
    }
}

private val scrim = OrbitScrim()

/** The one request shape for hero art, so neighbour prefetches land in the same memory-cache entry. */
fun heroRequest(context: Context, url: String, crossfadeMs: Int = 0): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(HERO_W, HERO_H)
        .allowRgb565(false)
        .transformations(scrim)
        .apply { if (crossfadeMs > 0) crossfade(crossfadeMs) }
        .build()
