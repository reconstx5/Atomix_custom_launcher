package dev.atomix.launcher.ui.theme

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key

/**
 * Holding OK (centre or Enter) on the remote: once the key starts repeating, [onLongPress] runs and the
 * release is swallowed so the normal click does not also fire. Place it before `clickable`.
 */
fun Modifier.onLongPressOk(onLongPress: () -> Unit): Modifier = composed {
    val held = remember { booleanArrayOf(false) }
    onPreviewKeyEvent { e ->
        val code = e.key.nativeKeyCode
        if (code != NativeKeyEvent.KEYCODE_DPAD_CENTER && code != NativeKeyEvent.KEYCODE_ENTER) return@onPreviewKeyEvent false
        val repeat = e.nativeKeyEvent.repeatCount
        when {
            e.type == KeyEventType.KeyDown && repeat == 1 -> { held[0] = true; onLongPress(); true }
            e.type == KeyEventType.KeyDown && repeat > 1 -> true
            e.type == KeyEventType.KeyUp && held[0] -> { held[0] = false; true }
            else -> false
        }
    }
}
