package dev.atomix.launcher.shell

import dev.atomix.launcher.prefs.LauncherPrefs

/** A focusable item: which row, its stable key (channel id / package), and where it was. */
data class FocusTarget(val row: String, val key: String, val index: Int)

/**
 * Remembers the last focused item of one screen (saved, so it survives the launcher being killed) and finds the
 * best item to focus when the screen comes back: the same item; else the same row at the same index (clamped);
 * else the first item of the first non-empty row.
 */
class FocusMemory(private val prefs: LauncherPrefs, private val screen: String) {
    private var last: FocusTarget? = load()

    fun remember(t: FocusTarget) {
        if (t == last) return
        last = t
        prefs.setFocusKey(screen, "${t.row}|${t.index}|${t.key}")
    }

    fun current(): FocusTarget? = last

    fun resolve(rows: Map<String, List<String>>): FocusTarget? {
        val t = last
        if (t != null) {
            rows[t.row]?.indexOf(t.key)?.takeIf { it >= 0 }?.let { return FocusTarget(t.row, t.key, it) }
            for ((row, keys) in rows) {
                val i = keys.indexOf(t.key)
                if (i >= 0) return FocusTarget(row, t.key, i)
            }
            rows[t.row]?.takeIf { it.isNotEmpty() }?.let { keys ->
                val i = t.index.coerceIn(0, keys.size - 1)
                return FocusTarget(t.row, keys[i], i)
            }
        }
        return rows.entries.firstOrNull { it.value.isNotEmpty() }?.let { FocusTarget(it.key, it.value[0], 0) }
    }

    private fun load(): FocusTarget? {
        val parts = prefs.focusKey(screen)?.split('|', limit = 3) ?: return null
        if (parts.size != 3) return null
        return FocusTarget(parts[0], parts[2], parts[1].toIntOrNull() ?: 0)
    }
}
