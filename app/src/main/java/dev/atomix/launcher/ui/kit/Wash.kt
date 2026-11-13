package dev.atomix.launcher.ui.kit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * The two full-screen backgrounds: the launcher's ground, and the darker one behind tuning and errors. Each is an
 * ellipse (radii and centre as fractions of the screen) running through three colour stops.
 */
enum class WashKind(
    internal val radiusX: Float, internal val radiusY: Float,
    internal val centreX: Float, internal val centreY: Float,
    internal val colours: IntArray, internal val stops: FloatArray,
) {
    Ground(1.30f, 1.00f, 0.50f, -0.10f, intArrayOf(0xFF14263F.toInt(), 0xFF0B1626.toInt(), 0xFF070E1A.toInt()), floatArrayOf(0f, 0.46f, 1f)),
    Player(0.90f, 0.80f, 0.50f, 0.40f, intArrayOf(0xFF0F1C30.toInt(), 0xFF070E1A.toInt(), 0xFF05090F.toInt()), floatArrayOf(0f, 0.7f, 1f)),
}

private const val WASH_W = 480
private const val WASH_H = 270
private val washes = arrayOfNulls<ImageBitmap>(WashKind.entries.size)

/** Draws the gradient once, small; stretched, it costs one textured quad per frame instead of a full-screen shader. */
private fun washBitmap(kind: WashKind): ImageBitmap = washes[kind.ordinal] ?: run {
    val bmp = Bitmap.createBitmap(WASH_W, WASH_H, Bitmap.Config.ARGB_8888)
    val shader = RadialGradient(0f, 0f, 1f, kind.colours, kind.stops, Shader.TileMode.CLAMP)
    shader.setLocalMatrix(Matrix().apply {
        setScale(kind.radiusX * WASH_W, kind.radiusY * WASH_H)
        postTranslate(kind.centreX * WASH_W, kind.centreY * WASH_H)
    })
    Canvas(bmp).drawPaint(Paint().apply { this.shader = shader; isDither = true })
    bmp.asImageBitmap().also { washes[kind.ordinal] = it }
}

/** The ground colour with its radial wash, filling the screen. */
@Composable
fun Wash(kind: WashKind = WashKind.Ground, modifier: Modifier = Modifier) {
    Image(washBitmap(kind), contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
}
