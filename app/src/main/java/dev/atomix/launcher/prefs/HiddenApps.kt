package dev.atomix.launcher.prefs

/**
 * The TV's system entries hidden until the owner hides or shows anything: the Netflix observer, Disclaimer
 * Customization and the factory menu. None is an app anyone opens, and the factory menu must not be opened by mistake.
 */
val DEFAULT_HIDDEN = listOf(
    "com.mediatek.tv.service",
    "com.mstar.android.tv.disclaimercustomization",
    "mediatek.factorymenu.ui",
)

/**
 * Apps hidden from Atomix TV. They stay installed; they leave the Apps page and every row on Home, and keep their place
 * in a row, so showing one again puts it back where it was. The first change saves the whole list, which rules from then
 * on (the three defaults no longer apply).
 */
class HiddenApps(private val prefs: LauncherPrefs) {
    fun all(): List<String> = prefs.hiddenApps

    fun isHidden(pkg: String): Boolean = pkg in prefs.hiddenApps

    fun hide(pkg: String) {
        val list = prefs.hiddenApps
        prefs.hiddenApps = if (pkg in list) list else list + pkg
    }

    fun show(pkg: String) {
        prefs.hiddenApps = prefs.hiddenApps - pkg
    }
}
