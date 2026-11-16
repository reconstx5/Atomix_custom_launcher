package dev.atomix.launcher.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconContentTest {
    private fun pixels(w: Int, h: Int, visible: (Int, Int) -> Boolean) = IntArray(w * h) { i -> if (visible(i % w, i / w)) Color.RED else Color.TRANSPARENT }

    @Test fun bounds_ofTheVisiblePixels_asFractions() {
        val p = pixels(10, 10) { x, y -> x in 2..7 && y in 3..6 }
        assertEquals(IconContent(0.2f, 0.3f, 0.8f, 0.7f), contentBounds(p, 10, 10))
    }

    @Test fun bounds_ignoreFaintShadows() {
        val p = IntArray(100) { i -> if (i % 10 in 2..7 && i / 10 in 2..7) Color.RED else Color.argb(20, 0, 0, 0) }
        assertEquals(IconContent(0.2f, 0.2f, 0.8f, 0.8f), contentBounds(p, 10, 10))
    }

    @Test fun bounds_fullAndEmpty() {
        assertEquals(IconContent(0f, 0f, 1f, 1f), contentBounds(pixels(4, 4) { _, _ -> true }, 4, 4))
        assertNull(contentBounds(pixels(4, 4) { _, _ -> false }, 4, 4))
    }

    @Test fun measure_drawsTheIconAndFindsItsMark_keepingItsBounds() {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRect(25f, 25f, 75f, 75f, Paint().apply { color = Color.RED })
        val icon = BitmapDrawable(ApplicationProvider.getApplicationContext<android.content.Context>().resources, bmp).apply { setBounds(1, 2, 3, 4) }
        assertEquals(IconContent(0.25f, 0.25f, 0.75f, 0.75f), measureIconContent(icon, side = 64))
        assertEquals(android.graphics.Rect(1, 2, 3, 4), icon.bounds)
    }
}
