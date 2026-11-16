package dev.atomix.launcher.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.TypedValue

data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable,
    val launch: Intent,
    val iconQuality: IconQuality = IconQuality.Sharp,
)

/** Whether an app's own icon is sharp enough to draw large on Home, too small to, or missing (Android's default). */
enum class IconQuality { Sharp, Low, None }

/**
 * How good the icon [iconResource] is, from the [file] and [density] its xxhdpi lookup resolved to. A vector or
 * adaptive icon (an .xml) and a bitmap with no density are sharp; a bitmap is sharp from hdpi (240) up, as an mdpi
 * one (48 px) blurs when Home draws it larger. A file that could not be read counts as sharp, so it is drawn as today.
 */
fun iconQuality(iconResource: Int, file: String?, density: Int): IconQuality = when {
    iconResource == 0 -> IconQuality.None
    file == null || file.endsWith(".xml") || density == TypedValue.DENSITY_NONE -> IconQuality.Sharp
    (if (density == TypedValue.DENSITY_DEFAULT) DisplayMetrics.DENSITY_MEDIUM else density) >= DisplayMetrics.DENSITY_HIGH -> IconQuality.Sharp
    else -> IconQuality.Low
}

/**
 * The app's icon as Android decodes it (the TV's own density, through its icon cache: at 1080p xhdpi a 96 px icon,
 * more than Home's art slot needs), and its quality from the best file it has, looked up at xxhdpi without decoding it.
 */
private fun loadIcon(pm: PackageManager, ri: ResolveInfo): Pair<Drawable, IconQuality> {
    val id = ri.iconResource
    val quality = if (id == 0) IconQuality.None else runCatching {
        val value = TypedValue()
        pm.getResourcesForApplication(ri.activityInfo!!.applicationInfo).getValueForDensity(id, DisplayMetrics.DENSITY_XXHIGH, value, true)
        iconQuality(id, value.string?.toString(), value.density)
    }.getOrElse { iconQuality(id, null, 0) }
    return ri.loadIcon(pm) to quality
}

const val SELF_PACKAGE = "dev.atomix.launcher"

/** Atomix's own app tile lives in this package; it is listed like any other app. */
const val ATOMIX_ACTIVITY = "dev.atomix.launcher.atomix.AtomixActivity"

/** Launchable apps: TV (leanback) activities win over phone ones; one entry per package; never this launcher (but Atomix's tile, yes). */
class InstalledApps(private val pm: PackageManager) {
    fun list(): List<AppEntry> {
        val seen = LinkedHashMap<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if ((pkg == SELF_PACKAGE && ai.name != ATOMIX_ACTIVITY) || pkg in seen) continue
                val launch = Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(pkg, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val (icon, quality) = loadIcon(pm, ri)
                seen[pkg] = AppEntry(
                    packageName = pkg,
                    label = ri.loadLabel(pm).toString(),
                    banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo?.loadBanner(pm) }.getOrNull(),
                    icon = icon,
                    launch = launch,
                    iconQuality = quality,
                )
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }
}
