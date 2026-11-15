package dev.atomix.launcher.prefs

import dev.atomix.launcher.apps.AppEntry

/** The favourites key for Atomix's own app tile (same package as the launcher). */
const val ATOMIX_APP_KEY = "dev.atomix.launcher"

val DEFAULT_DOCK = listOf(
    "org.smarttube.stable",
    "tv.twitch.android.app",
    "com.amazon.amazonvideo.livingroom",
    "ar.tvplayer.tv",
    "com.phlox.tvwebbrowser",
)

/**
 * Favourite apps on Home. Saved order is kept; apps no longer installed, or hidden from Atomix TV ([hidden]), are not
 * shown but not forgotten.
 */
class FavouriteApps(
    private val prefs: LauncherPrefs,
    private val hidden: (String) -> Boolean = { false },
    private val apps: () -> List<AppEntry>,
) {
    fun visible(): List<AppEntry> {
        val byPkg = apps().associateBy { it.packageName }
        return prefs.favouriteApps.filterNot(hidden).mapNotNull { byPkg[it] }
    }

    fun contains(pkg: String): Boolean = pkg in prefs.favouriteApps

    fun add(pkg: String) {
        val s = prefs.favouriteApps
        if (pkg !in s) prefs.favouriteApps = s + pkg
    }

    fun remove(pkg: String) {
        prefs.favouriteApps = prefs.favouriteApps - pkg
    }

    /** Moves [pkg] to [toVisibleIndex] among the visible favourites; hidden ones keep their slots. */
    fun move(pkg: String, toVisibleIndex: Int) {
        val order = visible().map { it.packageName }.toMutableList()
        if (!order.remove(pkg)) return
        order.add(toVisibleIndex.coerceIn(0, order.size), pkg)
        setVisibleOrder(order)
    }

    /** Puts the visible favourites in [pkgs] order, drops visible ones not listed, keeps hidden ones in place. */
    fun setVisibleOrder(pkgs: List<String>) {
        val visibleSet = visible().map { it.packageName }.toSet()
        val queue = ArrayDeque(pkgs.filter { it in visibleSet })
        val result = mutableListOf<String>()
        for (p in prefs.favouriteApps) {
            if (p in visibleSet) queue.removeFirstOrNull()?.let(result::add) else result += p
        }
        result += queue
        prefs.favouriteApps = result
    }
}
