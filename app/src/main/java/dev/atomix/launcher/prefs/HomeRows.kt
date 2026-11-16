package dev.atomix.launcher.prefs

import org.json.JSONArray
import org.json.JSONObject

/** What a row on Home holds: apps the owner chose, the server's "On now" channels, or the TV's inputs. */
enum class RowKind { Apps, OnNow, Inputs }

/** An app row's order: as the owner arranged it, or by name. */
enum class RowSort(val label: String) { YourOrder("Your order"), AtoZ("A to Z") }

/**
 * One row of Home. An app row keeps every app put in it, in its own order, including apps uninstalled or hidden from
 * Atomix TV: those are not drawn but keep their place.
 */
data class HomeRowSpec(
    val id: String,
    val kind: RowKind,
    val name: String = "",
    val apps: List<String> = emptyList(),
    val sort: RowSort = RowSort.YourOrder,
    val shown: Boolean = true,
)

/** The ids of the rows made from phase 2's Home, which its remembered focus already uses, and of the Inputs row. */
const val ROW_APPS = "apps"
const val ROW_ON_NOW = "onNow"
const val ROW_INPUTS = "inputs"

const val MAX_APP_ROWS = 8
const val MAX_NAME = 24

private const val YOUR_APPS = "Your apps"
private const val NEW_ID = "row-"

/** Phase 2's Home as rows: "Your apps" holding [favourites], and "On now", in [order]; then Inputs, hidden. */
fun firstRows(favourites: List<String>, order: RowOrder): List<HomeRowSpec> {
    val apps = HomeRowSpec(ROW_APPS, RowKind.Apps, YOUR_APPS, favourites.distinct())
    val onNow = HomeRowSpec(ROW_ON_NOW, RowKind.OnNow)
    val twoRows = if (order == RowOrder.AppsFirst) listOf(apps, onNow) else listOf(onNow, apps)
    return twoRows + HomeRowSpec(ROW_INPUTS, RowKind.Inputs, shown = false)
}

fun encodeRows(rows: List<HomeRowSpec>): String = JSONArray().apply {
    rows.forEach { r ->
        put(
            JSONObject().put("id", r.id).put("kind", r.kind.name).put("name", r.name)
                .put("apps", JSONArray(r.apps)).put("sort", r.sort.name).put("shown", r.shown),
        )
    }
}.toString()

/**
 * The saved rows, or null when there are none, they cannot be read, or they break a rule Home relies on (exactly one
 * "On now" and one Inputs row, 1 to 8 app rows, no id twice). Names longer than [MAX_NAME] are cut; an app listed
 * twice in a row is kept once, in its first place.
 */
fun decodeRows(s: String?): List<HomeRowSpec>? {
    if (s.isNullOrEmpty()) return null
    val rows = runCatching {
        val array = JSONArray(s)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val apps = o.optJSONArray("apps") ?: JSONArray()
            HomeRowSpec(
                id = o.getString("id"),
                kind = RowKind.valueOf(o.getString("kind")),
                name = o.optString("name").take(MAX_NAME),
                // Once each: Home keys a row's tiles by package, so a repeat would stop it drawing.
                apps = List(apps.length()) { apps.getString(it) }.distinct(),
                sort = RowSort.entries.firstOrNull { it.name == o.optString("sort") } ?: RowSort.YourOrder,
                shown = o.optBoolean("shown", true),
            )
        }
    }.getOrNull() ?: return null
    val valid = rows.count { it.kind == RowKind.OnNow } == 1 && rows.count { it.kind == RowKind.Inputs } == 1 &&
        rows.count { it.kind == RowKind.Apps } in 1..MAX_APP_ROWS && rows.map { it.id }.toSet().size == rows.size
    if (!valid) return null
    // A list with every app row hidden cannot be made here, but a Home without one would have no "All apps": show the first.
    if (rows.none { it.kind == RowKind.Apps && it.shown }) {
        val first = rows.first { it.kind == RowKind.Apps }.id
        return rows.map { if (it.id == first) it.copy(shown = true) else it }
    }
    return rows
}

private fun List<HomeRowSpec>.row(id: String): HomeRowSpec? = firstOrNull { it.id == id }
private fun List<HomeRowSpec>.edit(id: String, change: (HomeRowSpec) -> HomeRowSpec) = map { if (it.id == id) change(it) else it }
private val List<HomeRowSpec>.shownAppRows get() = count { it.kind == RowKind.Apps && it.shown }

/** False only for the last shown app row: Home keeps at least one. */
fun List<HomeRowSpec>.canHide(id: String): Boolean {
    val r = row(id) ?: return false
    return !(r.kind == RowKind.Apps && r.shown && shownAppRows == 1)
}

/** App rows only, never the last one, and never the last shown one (that would leave Home with no app row). */
fun List<HomeRowSpec>.canDelete(id: String): Boolean {
    val r = row(id) ?: return false
    return r.kind == RowKind.Apps && count { it.kind == RowKind.Apps } > 1 && canHide(id)
}

fun List<HomeRowSpec>.canAddRow(): Boolean = count { it.kind == RowKind.Apps } < MAX_APP_ROWS

/** "row-" and one more than the highest number any row-N here has, so a new row never takes a row's id. */
fun List<HomeRowSpec>.nextRowId(): String =
    NEW_ID + ((mapNotNull { it.id.removePrefix(NEW_ID).takeIf { _ -> it.id.startsWith(NEW_ID) }?.toIntOrNull() }.maxOrNull() ?: 0) + 1)

/** The row [by] places further down (negative: up); unchanged if that would leave the list. */
fun List<HomeRowSpec>.moved(id: String, by: Int): List<HomeRowSpec> {
    val from = indexOfFirst { it.id == id }
    val to = from + by
    if (from < 0 || to !in indices || by == 0) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

fun List<HomeRowSpec>.withShown(id: String, shown: Boolean): List<HomeRowSpec> =
    if (!shown && !canHide(id)) this else edit(id) { it.copy(shown = shown) }

fun List<HomeRowSpec>.withSort(id: String, sort: RowSort): List<HomeRowSpec> =
    edit(id) { if (it.kind == RowKind.Apps) it.copy(sort = sort) else it }

fun List<HomeRowSpec>.renamed(id: String, name: String): List<HomeRowSpec> =
    edit(id) { if (it.kind == RowKind.Apps) it.copy(name = name.take(MAX_NAME)) else it }

/** A new, empty, shown app row called [name] at the end; unchanged when there are already [MAX_APP_ROWS]. */
fun List<HomeRowSpec>.withRowAdded(name: String): List<HomeRowSpec> =
    if (!canAddRow()) this else this + HomeRowSpec(nextRowId(), RowKind.Apps, name.take(MAX_NAME))

fun List<HomeRowSpec>.deleted(id: String): List<HomeRowSpec> = if (canDelete(id)) filterNot { it.id == id } else this

/** Puts [pkg] at the end of the row, once, or takes it out of that row only. */
fun List<HomeRowSpec>.withApp(id: String, pkg: String, inRow: Boolean): List<HomeRowSpec> = edit(id) {
    when {
        it.kind != RowKind.Apps -> it
        inRow -> if (pkg in it.apps) it else it.copy(apps = it.apps + pkg)
        else -> it.copy(apps = it.apps - pkg)
    }
}

/**
 * The row's drawn apps ([isDrawn]) take [drawnOrder]; a drawn app left out of it leaves the row. Apps not drawn
 * (uninstalled or hidden) keep their slots.
 */
fun List<HomeRowSpec>.reordered(id: String, drawnOrder: List<String>, isDrawn: (String) -> Boolean): List<HomeRowSpec> =
    edit(id) { r ->
        val drawn = r.apps.filter(isDrawn).toSet()
        val queue = ArrayDeque(drawnOrder.filter { it in drawn }.distinct())
        val result = mutableListOf<String>()
        for (p in r.apps) if (p in drawn) queue.removeFirstOrNull()?.let(result::add) else result += p
        r.copy(apps = result + queue)
    }
