package dev.atomix.launcher.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Home's rows, saved as one value. The first read with nothing saved (or something unreadable) builds them from
 * phase 2's settings and saves them at once; phase 2's own keys are only read.
 */
class RowStore(private val prefs: LauncherPrefs) {
    private val _rows = MutableStateFlow(load())
    val rows: StateFlow<List<HomeRowSpec>> = _rows

    /** Applies [edit]; saves and tells everyone only when something changed. */
    fun update(edit: (List<HomeRowSpec>) -> List<HomeRowSpec>) {
        val next = edit(_rows.value)
        if (next == _rows.value) return
        prefs.homeRows = encodeRows(next)
        _rows.value = next
    }

    private fun load(): List<HomeRowSpec> = decodeRows(prefs.homeRows)
        ?: firstRows(prefs.favouriteApps, prefs.legacyRowOrder).also { prefs.homeRows = encodeRows(it) }
}
