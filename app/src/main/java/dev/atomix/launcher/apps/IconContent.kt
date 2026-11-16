package dev.atomix.launcher.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Where an icon's visible pixels sit, as fractions of its width and height (0 = left/top edge, 1 = right/bottom). */
data class IconContent(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A pixel counts as part of the mark from this alpha up, so a faint shadow round an icon does not. */
private const val VISIBLE_ALPHA = 48

/** The box round the visible pixels of [pixels] (ARGB, [w] × [h], row by row); null when nothing is visible. */
fun contentBounds(pixels: IntArray, w: Int, h: Int): IconContent? {
    var l = w; var t = h; var r = -1; var b = -1
    for (y in 0 until h) for (x in 0 until w) {
        if (pixels[y * w + x] ushr 24 >= VISIBLE_ALPHA) {
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
    }
    if (r < 0) return null
    return IconContent(l / w.toFloat(), t / h.toFloat(), (r + 1) / w.toFloat(), (b + 1) / h.toFloat())
}

/**
 * Where [icon]'s mark sits: drawn once into a small [side] × [side] bitmap and measured, so an icon with empty space
 * built in can be drawn larger on Home. Done when the apps are listed, never while a row draws. The icon's own bounds
 * are put back; null when it cannot be drawn or has nothing visible.
 */
fun measureIconContent(icon: Drawable, side: Int = 48): IconContent? = runCatching {
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val before = Rect(icon.bounds)
    try {
        icon.setBounds(0, 0, side, side)
        icon.draw(Canvas(bitmap))
        val pixels = IntArray(side * side).also { bitmap.getPixels(it, 0, side, 0, 0, side, side) }
        contentBounds(pixels, side, side)
    } finally {
        icon.bounds = before
        bitmap.recycle()
    }
}.getOrNull()
