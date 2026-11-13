package dev.atomix.launcher.ui.apps

import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.FavouriteApps
import kotlinx.coroutines.flow.StateFlow

/** What the Apps tab and manage apps need: every installed app, and whether each is in Home's "Your apps" row. */
class AppsTabState(private val favourites: FavouriteApps, private val catalog: AppCatalog) {
    val apps: StateFlow<List<AppEntry>> get() = catalog.apps
    fun list(): List<AppEntry> = catalog.apps.value
    fun isFavourite(pkg: String): Boolean = favourites.contains(pkg)

    /** Adds to the end of Home's apps row, or takes it off. */
    fun toggle(pkg: String) = if (isFavourite(pkg)) favourites.remove(pkg) else favourites.add(pkg)

    /** The apps manage apps shows under [filter], in the list's own order. */
    fun shown(filter: AppFilter): List<AppEntry> = when (filter) {
        AppFilter.All -> list()
        AppFilter.InRow -> list().filter { isFavourite(it.packageName) }
    }

    /** The filter's pill, with how many apps it holds: "All · 26", "In this row · 6". */
    fun pillLabel(filter: AppFilter): String = when (filter) {
        AppFilter.All -> "All"
        AppFilter.InRow -> "In this row"
    } + " \u00B7 " + shown(filter).size

    /** What an app's menu offers. Nothing for an app that is no longer installed: its menu closes. */
    fun menuFor(pkg: String): List<AppMenuItem> {
        if (catalog.byPackage(pkg) == null) return emptyList()
        return listOf(if (isFavourite(pkg)) AppMenuItem.Remove else AppMenuItem.Add, AppMenuItem.Info)
    }
}

/** Which apps manage apps lists. ("Hidden" joins these when hiding arrives.) */
enum class AppFilter { All, InRow }

/** One line of an app's menu in manage apps. */
enum class AppMenuItem(val label: String) {
    Add("Add to Your apps"),
    Remove("Remove from Your apps"),
    Info("App info"),
}
