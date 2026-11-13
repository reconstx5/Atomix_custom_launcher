package dev.atomix.launcher.ui.home

import android.view.KeyEvent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import dev.atomix.launcher.shell.EditKey
import dev.atomix.launcher.shell.EditSession
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/** What a row's heading says in place of "Edit" while that row is being rearranged. */
@Composable
fun EditStatus(session: EditSession, modifier: Modifier = Modifier) {
    val pos = session.position()
    BasicText(
        if (pos != null) "Moving · ${pos.first} of ${pos.second}" else "Editing",
        // The heading shifts its action 16 px right for the "Edit" button's padding; this is plain text.
        modifier.padding(end = px(16)),
        style = AtomixType.subheading().copy(fontWeight = FontWeight.Normal),
        maxLines = 1,
    )
}

/** The held item rides 12 px above its row. */
@Composable
fun Modifier.lifted(held: Boolean): Modifier {
    val lift = with(LocalDensity.current) { px(12).toPx() }
    return if (held) graphicsLayer { translationY = -lift } else this
}

/**
 * The remote while a row is being rearranged: OK, and the arrows as [EditKey.Front] (Left, Up) and [EditKey.End]
 * (Right, Down). [onKey] says whether an arrow was used; an unused arrow goes on to move focus. OK never reaches the
 * item under it, press or release, because there it would open the app or play the channel.
 */
fun Modifier.editKeys(active: Boolean, onKey: (EditKey) -> Boolean): Modifier = if (!active) this else onPreviewKeyEvent { e ->
    val key = when (e.nativeKeyEvent.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> EditKey.Ok
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> EditKey.Front
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> EditKey.End
        else -> return@onPreviewKeyEvent false
    }
    val down = e.type == KeyEventType.KeyDown
    when {
        key == EditKey.Ok -> {
            if (down && e.nativeKeyEvent.repeatCount == 0) onKey(key)
            true
        }
        down -> onKey(key)
        else -> false
    }
}
