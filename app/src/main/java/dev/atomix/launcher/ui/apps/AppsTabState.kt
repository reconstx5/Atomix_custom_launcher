package dev.atomix.launcher.ui.apps

import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.FavouriteApps
import kotlinx.coroutines.flow.StateFlow

/** What the Apps tab needs: every installed app, and whether each is on Home. */
class AppsTabState(private val favourites: FavouriteApps, private val catalog: AppCatalog) {
    val apps: StateFlow<List<AppEntry>> get() = catalog.apps
    fun list(): List<AppEntry> = catalog.apps.value
    fun isFavourite(pkg: String): Boolean = favourites.contains(pkg)

    /** Adds to the end of Home's apps row, or takes it off. */
    fun toggle(pkg: String) = if (isFavourite(pkg)) favourites.remove(pkg) else favourites.add(pkg)
}
