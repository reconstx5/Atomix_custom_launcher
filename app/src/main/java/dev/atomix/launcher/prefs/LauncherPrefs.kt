package dev.atomix.launcher.prefs

import android.content.SharedPreferences

/**
 * Launcher-only settings (not secret, so plain prefs): favourite apps, reduced effects, remembered focus.
 * The first read of [favouriteApps] migrates the old dock order, with Atomix's own tile added first.
 */
class LauncherPrefs(private val prefs: SharedPreferences, private val legacyDock: () -> List<String>?) {
    var favouriteApps: List<String>
        get() {
            val saved = prefs.getString(K_FAVOURITES, null)
            if (saved != null) return if (saved.isEmpty()) emptyList() else saved.split('\n')
            val legacy = legacyDock() ?: DEFAULT_DOCK
            val migrated = if (ATOMIX_APP_KEY in legacy) legacy else listOf(ATOMIX_APP_KEY) + legacy
            favouriteApps = migrated
            return migrated
        }
        set(v) { prefs.edit().putString(K_FAVOURITES, v.joinToString("\n")).apply() }

    var reducedEffects: Boolean
        get() = prefs.getBoolean(K_REDUCED, false)
        set(v) { prefs.edit().putBoolean(K_REDUCED, v).apply() }

    fun focusKey(screen: String): String? = prefs.getString(K_FOCUS + screen, null)

    fun setFocusKey(screen: String, key: String?) {
        prefs.edit().apply { if (key == null) remove(K_FOCUS + screen) else putString(K_FOCUS + screen, key) }.apply()
    }

    private companion object {
        const val K_FAVOURITES = "favourite_apps"
        const val K_REDUCED = "reduced_effects"
        const val K_FOCUS = "focus_"
    }
}
