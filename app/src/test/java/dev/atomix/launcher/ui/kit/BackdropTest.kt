package dev.atomix.launcher.ui.kit

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackdropTest {
    private fun solid(w: Int, h: Int, color: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun blurredCopy_is240x135() {
        val out = Backdrop.blurredCopy(solid(1920, 1080, Color.BLUE))
        assertEquals(240, out.width)
        assertEquals(135, out.height)
    }

    @Test fun blurredCopy_ofUniformImage_staysUniform() {
        val c = Color.rgb(40, 120, 200)
        val out = Backdrop.blurredCopy(solid(480, 270, c))
        for (x in listOf(0, 60, 239)) for (y in listOf(0, 70, 134)) {
            val p = out.getPixel(x, y)
            assertTrue(abs(Color.red(p) - 40) <= 1 && abs(Color.green(p) - 120) <= 1 && abs(Color.blue(p) - 200) <= 1)
        }
    }

    @Test fun blurredCopy_softensAHardEdge() {
        val src = solid(240, 135, Color.BLACK)
        for (x in 120 until 240) for (y in 0 until 135) src.setPixel(x, y, Color.WHITE)
        val out = Backdrop.blurredCopy(src)
        val nearEdge = Color.red(out.getPixel(118, 60))
        assertTrue("left of the edge picks up light: $nearEdge", nearEdge in 20..230)
    }

    @Test fun dimmed_halvesWhite() {
        val out = Backdrop.dimmed(solid(10, 10, Color.WHITE), 0.5f)
        val p = out.getPixel(5, 5)
        assertTrue(abs(Color.red(p) - 128) <= 2 && abs(Color.blue(p) - 128) <= 2)
        assertEquals(255, Color.alpha(p))
    }
}
