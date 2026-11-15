package dev.atomix.launcher.prefs

/** Row fixtures shared by every row test (pre-flight ruling): phase 2's Home with two apps, and broken saved values. */
internal fun base(): List<HomeRowSpec> = firstRows(listOf("a", "b"), RowOrder.AppsFirst)

private fun row(id: String, kind: String, name: String = "") =
    """{"id":"$id","kind":"$kind","name":"$name","apps":[],"sort":"YourOrder","shown":true}"""

internal fun savedRows(vararg rows: String) = rows.joinToString(",", "[", "]")

internal val noAppRow = savedRows(row("onNow", "OnNow"), row("inputs", "Inputs"))
internal val twoOnNow = savedRows(row("apps", "Apps"), row("onNow", "OnNow"), row("onNow2", "OnNow"), row("inputs", "Inputs"))
internal val repeatedId = savedRows(row("apps", "Apps"), row("apps", "Apps"), row("onNow", "OnNow"), row("inputs", "Inputs"))
internal val unknownKind = savedRows(row("apps", "Apps"), row("onNow", "OnNow"), row("inputs", "Inputs"), row("x", "Clock"))

/** A readable saved list whose first row, "Your apps", is called [name]. */
internal fun withName(name: String) = savedRows(row("apps", "Apps", name), row("onNow", "OnNow"), row("inputs", "Inputs"))
