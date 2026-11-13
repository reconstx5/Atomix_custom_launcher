package dev.atomix.launcher.apps

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class InstalledAppsTest {
    private val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager

    private fun install(pkg: String, label: String, vararg categories: String) = installActivity(pkg, "$pkg.Main", label, *categories)

    private fun installActivity(pkg: String, activity: String, label: String, vararg categories: String) {
        val app = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
        shadowOf(pm).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = app })
        categories.forEach { cat ->
            val ri = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = pkg; name = activity; applicationInfo = app; nonLocalizedLabel = label }
                nonLocalizedLabel = label
            }
            shadowOf(pm).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(cat), ri)
        }
    }

    @Test fun excludesSelf_andDedupesByPackage() {
        install("com.both", "Both", Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        install("com.alpha", "Alpha", Intent.CATEGORY_LAUNCHER)
        install("dev.atomix.launcher", "Atomix", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val list = InstalledApps(pm).list()
        assertEquals(listOf("com.alpha", "com.both"), list.map { it.packageName })
        assertEquals(listOf("Alpha", "Both"), list.map { it.label })
    }

    @Test fun includesAtomixTile_excludesLauncherItself() {
        installActivity(SELF_PACKAGE, "dev.atomix.launcher.HomeActivity", "Atomix TV", Intent.CATEGORY_LEANBACK_LAUNCHER)
        installActivity(SELF_PACKAGE, ATOMIX_ACTIVITY, "Atomix", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val list = InstalledApps(pm).list()
        assertEquals(listOf(ATOMIX_ACTIVITY), list.filter { it.packageName == SELF_PACKAGE }.map { it.launch.component!!.className })
        assertEquals("Atomix", list.single { it.packageName == SELF_PACKAGE }.label)
    }
}
