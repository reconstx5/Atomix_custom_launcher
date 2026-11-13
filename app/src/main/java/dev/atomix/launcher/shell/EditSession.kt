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

    /**
     * One remote key while the row is being edited; [focused] is the item focus is on. OK picks that item up, or puts
     * the held one down; Back puts the held one down where it is, or ends editing; the arrows move the held item and
     * are left to move focus when nothing is held.
     */
    fun onKey(key: EditKey, focused: String?): EditOutcome {
        val holding = held != null
        return when (key) {
            EditKey.Ok -> when {
                holding -> { place(); EditOutcome.Placed }
                focused != null && focused in order -> { pickUp(focused); EditOutcome.Handled }
                else -> EditOutcome.Ignored
            }
            EditKey.Back -> if (holding) { place(); EditOutcome.Placed } else EditOutcome.Finished
            EditKey.Front, EditKey.End -> {
                if (!holding) return EditOutcome.Ignored
                move(if (key == EditKey.Front) -1 else 1)
                EditOutcome.Handled
            }
        }
    }

    /** Where the held item is, 1-based, as (n, m); null with nothing held. */
    fun position(): Pair<Int, Int>? = held?.let { order.indexOf(it) + 1 to order.size }
}

/** The remote keys edit mode understands: Front is Left or Up (towards the front), End is Right or Down. */
enum class EditKey { Ok, Back, Front, End }

/** What a key did: nothing (focus may move), something, put the held item down, or ended editing. */
enum class EditOutcome { Ignored, Handled, Placed, Finished }
