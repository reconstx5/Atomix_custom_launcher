package dev.atomix.launcher.prefs

import android.content.SharedPreferences

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
 * Launcher-only settings (not secret, so plain prefs): Home's rows, hidden apps, reduced effects, remembered focus.
 * Phase 2's "Your apps" ([favouriteApps]) and row order ([legacyRowOrder]) are only read, to build the rows once.
 */
class LauncherPrefs(private val prefs: SharedPreferences, private val legacyDock: () -> List<String>?) {
    /** Phase 2's "Your apps"; before that was ever saved, the old dock with Atomix's own tile first. Never written. */
    val favouriteApps: List<String>
        get() {
            val saved = prefs.getString(K_FAVOURITES, null)
            if (saved != null) return if (saved.isEmpty()) emptyList() else saved.split('\n')
            val legacy = legacyDock() ?: DEFAULT_DOCK
            return if (ATOMIX_APP_KEY in legacy) legacy else listOf(ATOMIX_APP_KEY) + legacy
        }

    /** Phase 2's row order; never written. */
    val legacyRowOrder: RowOrder get() = enumOr(prefs.getString(K_ROW_ORDER, null), RowOrder.AppsFirst)

    /** Home's rows as saved by [RowStore]; null until the first start of phase 3. */
    var homeRows: String?
        get() = prefs.getString(K_ROWS, null)
        set(v) { prefs.edit().putString(K_ROWS, v).apply() }

    /** Apps hidden from Atomix TV ([HiddenApps]). Until anything is saved, [DEFAULT_HIDDEN]; a saved "" is none. */
    var hiddenApps: List<String>
        get() {
            val saved = prefs.getString(K_HIDDEN, null) ?: return DEFAULT_HIDDEN
            return if (saved.isEmpty()) emptyList() else saved.split('\n')
        }
        set(v) { prefs.edit().putString(K_HIDDEN, v.joinToString("\n")).apply() }

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

    /** How Home is laid out. A saved name this build does not know reads as that setting's default. */
    var homeLayout: HomeLayout
        get() = HomeLayout(
            tileSize = enumOr(prefs.getString(K_TILE_SIZE, null), TileSize.Medium),
            tileShape = enumOr(prefs.getString(K_TILE_SHAPE, null), TileShape.Wide),
            spacing = enumOr(prefs.getString(K_SPACING, null), Spacing.Comfortable),
            appNames = prefs.getBoolean(K_APP_NAMES, true),
            rowOrder = enumOr(prefs.getString(K_ROW_ORDER, null), RowOrder.AppsFirst),
            onNow = enumOr(prefs.getString(K_ON_NOW, null), OnNowLayout.LargeTwo),
        )
        set(v) {
            prefs.edit()
                .putString(K_TILE_SIZE, v.tileSize.name).putString(K_TILE_SHAPE, v.tileShape.name).putString(K_SPACING, v.spacing.name)
                .putBoolean(K_APP_NAMES, v.appNames).putString(K_ROW_ORDER, v.rowOrder.name).putString(K_ON_NOW, v.onNow.name)
                .apply()
        }

    fun focusKey(screen: String): String? = prefs.getString(K_FOCUS + screen, null)

    fun setFocusKey(screen: String, key: String?) {
        prefs.edit().apply { if (key == null) remove(K_FOCUS + screen) else putString(K_FOCUS + screen, key) }.apply()
    }

    private companion object {
        const val K_FAVOURITES = "favourite_apps"
        const val K_ROWS = "home_rows"
        const val K_REDUCED = "reduced_effects"
        const val K_HIDDEN = "hidden_apps"
        const val K_FOCUS = "focus_"
        const val K_TILE_SIZE = "home_tile_size"
        const val K_TILE_SHAPE = "home_tile_shape"
        const val K_SPACING = "home_spacing"
        const val K_APP_NAMES = "home_app_names"
        const val K_ROW_ORDER = "home_row_order"
        const val K_ON_NOW = "home_on_now"
        const val K_THEME = "look_theme"
        const val K_ACCENT = "look_accent"
        const val K_TEXT = "look_text"
        const val K_CLOCK = "look_clock"
        const val K_FOCUS_STYLE = "look_focus"
    }
}
