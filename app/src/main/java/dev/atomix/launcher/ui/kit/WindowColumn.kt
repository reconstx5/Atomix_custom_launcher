package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import dev.atomix.launcher.ui.theme.px
import kotlin.math.max
import kotlin.math.min

/** Which item of a [WindowColumn] is the first of those that show. */
class WindowState(first: Int = 0) {
    var first by mutableIntStateOf(first)

    /** Moves the window just far enough to show item [index] of [count], [visible] at a time. */
    fun show(index: Int, count: Int, visible: Int) {
        val start = startFor(count, visible)
        first = when {
            index < start -> index
            index > start + visible - 1 -> index - visible + 1
            else -> start
        }
    }

    /** Whether item [index] of [count] is one of the [visible] that show. Only those are certain to be built. */
    fun shows(index: Int, count: Int, visible: Int): Boolean {
        if (index !in 0 until count) return false
        val start = startFor(count, visible)
        return index in start until start + visible
    }

    /** [first] kept inside a list of [count] items. */
    fun startFor(count: Int, visible: Int): Int = first.coerceIn(0, max(count - visible, 0))
}

/**
 * A list that shows [visible] rows at a time and follows focus, without being a scrolling container: a scrolling list
 * clips to its edges, which would cut the focus glow, and its rows come and go as it scrolls.
 *
 * Only the rows that show are drawn. The row just above and the row just below are also there, unseen, and can take
 * focus while focus is in the list: that is what gives Up and Down somewhere to go, and stepping onto one moves the
 * window by a row. So the focused row is never removed, and nothing moves focus but the remote. There is no scroll
 * animation.
 *
 * [row] must apply `slot` first on its outermost element.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> WindowColumn(
    items: List<T>,
    key: (index: Int, item: T) -> Any,
    state: WindowState,
    visible: Int,
    rowPx: Int,
    gapPx: Int,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, item: T, slot: Modifier) -> Unit,
) {
    var inside by remember { mutableStateOf(false) }
    val first = state.startFor(items.size, visible)
    val step = px(rowPx + gapPx)
    val firstBuilt = max(first - 1, 0)
    val lastBuilt = min(first + visible, items.lastIndex)
    Box(modifier.height(px(visible * rowPx + (visible - 1) * gapPx)).onFocusChanged { inside = it.hasFocus }) {
        for (index in firstBuilt..lastBuilt) {
            val item = items[index]
            key(key(index, item)) {
                val shown = index in first until first + visible
                row(
                    index, item,
                    Modifier
                        .offset(y = step * (index - first))
                        .graphicsLayer { alpha = if (shown) 1f else 0f }
                        .focusProperties {
                            canFocus = shown || inside
                            // A key that arrives before the next row has been built (the remote held down) stops
                            // here for a frame; it must not find something outside the list instead.
                            if (index == firstBuilt && index > 0) up = FocusRequester.Cancel
                            if (index == lastBuilt && index < items.lastIndex) down = FocusRequester.Cancel
                        }
                        .onFocusChanged { if (it.hasFocus) state.show(index, items.size, visible) },
                )
            }
        }
    }
}
