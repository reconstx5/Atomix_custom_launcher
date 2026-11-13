package dev.atomix.launcher.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Edit mode for one Home row: OK picks an item up, ◀/▶ move it live, OK places it, ▼ removes, Back cancels the move.
 * Keys are channel ids or package names. Observable, so the row redraws as the order changes.
 */
class EditSession(private val initial: List<String>) {
    var order: List<String> by mutableStateOf(initial)
        private set
    var held: String? by mutableStateOf(null)
        private set

    /** The order when the held item was picked up, for [cancelMove]. */
    private var beforePickUp: List<String>? = null

    val changed: Boolean get() = order != initial

    fun pickUp(key: String) {
        if (key !in order) return
        held = key
        beforePickUp = order
    }

    /** Moves the held item [delta] places; nothing happens at the ends or with nothing held. */
    fun move(delta: Int) {
        val key = held ?: return
        val from = order.indexOf(key)
        val to = from + delta
        if (to !in order.indices) return
        order = order.toMutableList().apply { removeAt(from); add(to, key) }
    }

    fun place() {
        held = null
        beforePickUp = null
    }

    fun remove(key: String) {
        order = order - key
        if (held == key) place()
    }

    fun cancelMove() {
        beforePickUp?.let { order = it }
        place()
    }

    /** Where the held item is, 1-based, as (n, m); null with nothing held. */
    fun position(): Pair<Int, Int>? = held?.let { order.indexOf(it) + 1 to order.size }
}
