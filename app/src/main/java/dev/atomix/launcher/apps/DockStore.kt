package dev.atomix.launcher.apps

import dev.atomix.launcher.data.SessionStore

val DEFAULT_DOCK = listOf(
    "org.smarttube.stable",
    "tv.twitch.android.app",
    "com.amazon.amazonvideo.livingroom",
    "ar.tvplayer.tv",
    "com.phlox.tvwebbrowser",
)

/** The pinned apps row. Saved order is kept; apps no longer installed are hidden, not forgotten. */
class DockStore(private val store: SessionStore, private val apps: () -> List<AppEntry>) {
    private fun saved(): List<String> = store.dock ?: DEFAULT_DOCK

    fun dock(): List<AppEntry> {
        val byPkg = apps().associateBy { it.packageName }
        return saved().mapNotNull { byPkg[it] }
    }

    fun add(pkg: String) {
        val s = saved()
        if (pkg !in s) store.dock = s + pkg
    }

    fun remove(pkg: String) {
        store.dock = saved() - pkg
    }

    fun move(pkg: String, toIndex: Int) {
        val s = saved().toMutableList()
        if (!s.remove(pkg)) return
        s.add(toIndex.coerceIn(0, s.size), pkg)
        store.dock = s
    }
}
