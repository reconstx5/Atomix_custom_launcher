package dev.atomix.launcher.ui.home

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import dev.atomix.launcher.shell.EditSession
import dev.atomix.launcher.ui.kit.ChipButton
import dev.atomix.launcher.ui.kit.HintBar
import dev.atomix.launcher.ui.kit.LineIcon
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/**
 * A row header ("On now", "Apps") with its ✎ Edit chip, or — while that row is being edited and an item is held —
 * "◀ Moving ▶ · Position n of m". The chip overflows the 40 px header instead of pushing the rows down.
 */
@Composable
fun EditableHeader(title: String, session: EditSession?, canEdit: Boolean, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.height(px(40)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(24))) {
        BasicText(title, style = AtomixType.rowHeader())
        val pos = session?.position()
        when {
            pos != null -> BasicText("◀ Moving ▶  ·  Position ${pos.first} of ${pos.second}", style = AtomixType.small().copy(color = AtomixColors.TextPrimary))
            session != null -> BasicText("Editing", style = AtomixType.small())
            canEdit -> Box(Modifier.wrapContentHeight(unbounded = true)) { ChipButton("Edit", onEdit, icon = LineIcon.Edit) }
        }
    }
}

/** The always-visible remote hints while a row is in edit mode. */
@Composable
fun EditHints(session: EditSession, removeWord: String, modifier: Modifier = Modifier) {
    val text = if (session.held != null) "◀ ▶ Move · OK Place · ▼ $removeWord · Back Cancel"
    else "OK Pick up · ▼ $removeWord · Back Done"
    HintBar(text, modifier.padding(bottom = px(40)))
}

/** The held item rides 12 px above its row, clear of the header text. */
@Composable
fun Modifier.lifted(held: Boolean): Modifier {
    val lift = with(LocalDensity.current) { px(12).toPx() }
    return if (held) graphicsLayer { translationY = -lift } else this
}

/** Remote keys while a row is in edit mode; see [EditKeys]. */
fun Modifier.editKeys(keys: EditKeys?): Modifier = if (keys == null) this else onPreviewKeyEvent { e ->
    val code = e.nativeKeyEvent.keyCode
    if (code !in EditKeys.HANDLED) return@onPreviewKeyEvent false
    if (e.type == KeyEventType.KeyDown) {
        val used = keys.onKey(code)
        if (code in EditKeys.SELECT) keys.selectUsed = used
        used
    } else {
        // OK places on key-down; its key-up must not reach the item (which would open it).
        code in EditKeys.SELECT && keys.selectUsed
    }
}

/** OK picks up or places, ◀/▶ move the held item, ▼ removes, ▲ stays in the row. Returns whether the key was used. */
class EditKeys(val onKey: (Int) -> Boolean) {
    var selectUsed = false

    companion object {
        val SELECT = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
        val HANDLED = SELECT + setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
    }
}
