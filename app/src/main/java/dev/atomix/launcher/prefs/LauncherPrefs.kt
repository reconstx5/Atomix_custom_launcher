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

    /** How the launcher looks. A saved name this build does not know reads as that setting's default. */
    var look: Look
        get() = Look(
            theme = enumOr(prefs.getString(K_THEME, null), ThemeId.Midnight),
            accent = enumOr(prefs.getString(K_ACCENT, null), AccentId.Ice),
            textSize = enumOr(prefs.getString(K_TEXT, null), TextSize.Standard),
            clock = ClockChoice.entries.firstOrNull { it.name == prefs.getString(K_CLOCK, null) },
            focus = enumOr(prefs.getString(K_FOCUS_STYLE, null), FocusStyle.RingAndGlow),
        )
        set(v) {
            prefs.edit()
                .putString(K_THEME, v.theme.name).putString(K_ACCENT, v.accent.name).putString(K_TEXT, v.textSize.name)
                .putString(K_FOCUS_STYLE, v.focus.name)
                .apply { if (v.clock == null) remove(K_CLOCK) else putString(K_CLOCK, v.clock.name) }
                .apply()
        }

    fun focusKey(screen: String): String? = prefs.getString(K_FOCUS + screen, null)

    fun setFocusKey(screen: String, key: String?) {
        prefs.edit().apply { if (key == null) remove(K_FOCUS + screen) else putString(K_FOCUS + screen, key) }.apply()
    }

    private companion object {
        const val K_FAVOURITES = "favourite_apps"
        const val K_REDUCED = "reduced_effects"
        const val K_FOCUS = "focus_"
        const val K_THEME = "look_theme"
        const val K_ACCENT = "look_accent"
        const val K_TEXT = "look_text"
        const val K_CLOCK = "look_clock"
        const val K_FOCUS_STYLE = "look_focus"
    }
}
