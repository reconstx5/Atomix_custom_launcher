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

    @Test fun iconQuality_rule() {
        assertEquals(IconQuality.None, iconQuality(0, null, 0))
        assertEquals(IconQuality.Sharp, iconQuality(1, "res/mipmap-anydpi-v26/ic_launcher.xml", 0xfffe))
        assertEquals(IconQuality.Sharp, iconQuality(1, "res/drawable/icon.xml", 0)) // a vector in drawable/
        assertEquals(IconQuality.Low, iconQuality(1, "res/drawable/icon.png", 0)) // no bucket counts as mdpi
        assertEquals(IconQuality.Low, iconQuality(1, "res/drawable-mdpi/icon.png", 160))
        assertEquals(IconQuality.Sharp, iconQuality(1, "res/drawable-hdpi/icon.png", 240))
        assertEquals(IconQuality.Sharp, iconQuality(1, "res/mipmap-xxhdpi/ic_launcher.png", 480))
        assertEquals(IconQuality.Sharp, iconQuality(1, "res/drawable-nodpi/icon.png", 0xffff))
        assertEquals(IconQuality.Sharp, iconQuality(1, null, 0)) // unreadable: drawn as today
    }

    @Test fun appWithNoIconResource_isNone() {
        install("com.bare", "Bare", Intent.CATEGORY_LEANBACK_LAUNCHER)
        assertEquals(IconQuality.None, InstalledApps(pm).list().single { it.packageName == "com.bare" }.iconQuality)
    }

    @Test fun iconIsDecodedAsAndroidDecodesIt_qualityFromItsBestFile() {
        // Decoding at xxhdpi would hold every icon ~2x larger for the life of Home: the TV's own density is enough.
        val app = ApplicationInfo().apply { packageName = SELF_PACKAGE; nonLocalizedLabel = "Atomix"; icon = dev.atomix.launcher.R.mipmap.ic_launcher }
        shadowOf(pm).installPackage(PackageInfo().apply { packageName = SELF_PACKAGE; applicationInfo = app })
        val ri = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = SELF_PACKAGE; name = ATOMIX_ACTIVITY; applicationInfo = app; nonLocalizedLabel = "Atomix" }
        }
        shadowOf(pm).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), ri)
        val entry = InstalledApps(pm).list().single { it.packageName == SELF_PACKAGE }
        assertEquals(ri.loadIcon(pm).intrinsicWidth, entry.icon.intrinsicWidth)
        assertEquals(IconQuality.Sharp, entry.iconQuality)
    }

    @Test fun includesAtomixTile_excludesLauncherItself() {
        installActivity(SELF_PACKAGE, "dev.atomix.launcher.HomeActivity", "Atomix TV", Intent.CATEGORY_LEANBACK_LAUNCHER)
        installActivity(SELF_PACKAGE, ATOMIX_ACTIVITY, "Atomix", Intent.CATEGORY_LEANBACK_LAUNCHER)
        val list = InstalledApps(pm).list()
        assertEquals(listOf(ATOMIX_ACTIVITY), list.filter { it.packageName == SELF_PACKAGE }.map { it.launch.component!!.className })
        assertEquals("Atomix", list.single { it.packageName == SELF_PACKAGE }.label)
    }
}
