package dev.atomix.launcher.ui.kit

import android.graphics.Bitmap

/**
 * Cheap "glass" for a TV with no real-time blur: one small blurred copy of the backdrop, made once per artwork
 * change (call off the main thread), which panels draw under themselves.
 */
object Backdrop {
    const val WIDTH = 240
    const val HEIGHT = 135
    private const val RADIUS = 6

    fun blurredCopy(src: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(src, WIDTH, HEIGHT, true)
        val px = IntArray(WIDTH * HEIGHT)
        small.getPixels(px, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        // Three box blurs approximate a Gaussian.
        repeat(3) {
            boxBlur(px, WIDTH, HEIGHT, RADIUS, horizontal = true)
            boxBlur(px, WIDTH, HEIGHT, RADIUS, horizontal = false)
        }
        val out = if (small === src) small.copy(Bitmap.Config.ARGB_8888, true) else small
        out.setPixels(px, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        return out
    }

    /** A darker copy (e.g. the backdrop while focus is in the apps row); [amount] 0 = unchanged, 1 = black. */
    fun dimmed(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val k = 1f - amount.coerceIn(0f, 1f)
        for (i in px.indices) {
            val c = px[i]
            val r = (((c shr 16) and 0xFF) * k + 0.5f).toInt()
            val g = (((c shr 8) and 0xFF) * k + 0.5f).toInt()
            val b = ((c and 0xFF) * k + 0.5f).toInt()
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Running-sum box blur along one axis, edges clamped. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val buf = IntArray(len)
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int) = if (horizontal) line * w + i else i * w + line
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) {
                val c = px[at(k.coerceIn(0, len - 1))]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (i in 0 until len) {
                buf[i] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                val out = px[at((i - r).coerceIn(0, len - 1))]
                val inn = px[at((i + r + 1).coerceIn(0, len - 1))]
                sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
            for (i in 0 until len) px[at(i)] = buf[i]
        }
    }
}
