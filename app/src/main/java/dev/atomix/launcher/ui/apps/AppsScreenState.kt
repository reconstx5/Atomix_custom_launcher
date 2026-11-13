package dev.atomix.launcher.ui.apps

import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.apps.DockStore

/** What the apps grid needs: the app list and whether each app is pinned to the dock. */
class AppsScreenState(private val dock: DockStore, private val apps: () -> List<AppEntry>) {
    fun list(): List<AppEntry> = apps()
    fun inDock(pkg: String): Boolean = dock.dock().any { it.packageName == pkg }
    fun addToDock(pkg: String) = dock.add(pkg)
    fun toggleDock(pkg: String) = if (inDock(pkg)) dock.remove(pkg) else dock.add(pkg)
}
