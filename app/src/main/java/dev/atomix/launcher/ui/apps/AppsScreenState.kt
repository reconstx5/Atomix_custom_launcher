package dev.atomix.launcher.ui.apps

import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.FavouriteApps

/** What the apps grid needs: the app list and whether each app is pinned to the dock. */
class AppsScreenState(private val dock: FavouriteApps, private val apps: () -> List<AppEntry>) {
    fun list(): List<AppEntry> = apps()
    fun inDock(pkg: String): Boolean = dock.contains(pkg)
    fun addToDock(pkg: String) = dock.add(pkg)
    fun toggleDock(pkg: String) = if (inDock(pkg)) dock.remove(pkg) else dock.add(pkg)
}
