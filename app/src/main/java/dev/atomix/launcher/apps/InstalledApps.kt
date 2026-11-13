package dev.atomix.launcher.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable,
    val launch: Intent,
)

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
                seen[pkg] = AppEntry(
                    packageName = pkg,
                    label = ri.loadLabel(pm).toString(),
                    banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo?.loadBanner(pm) }.getOrNull(),
                    icon = ri.loadIcon(pm),
                    launch = launch,
                )
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }
}
