package dev.atomix.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs

/**
 * The TV's own settings pages that Settings › TV settings opens, in the order they are listed. Each was opened on the
 * VEON TV and seen to show its page. [action] or [component] says how; Date & time has a component because its action
 * goes to a chooser. [note] is the one line a row needs when its page is not quite what its name says.
 *
 * The TV has no page of its own for remotes and accessories (they are drawn inside its main settings page), so that
 * row opens the main page. The factory-reset page and the accessory pairing search are deliberately not here.
 */
enum class TvPage(val label: String, val action: String?, val component: String? = null, val note: String? = null) {
    Network("Network & Internet", "android.settings.WIFI_SETTINGS"),
    DisplaySound("Display & Sound", "android.settings.SOUND_SETTINGS"),
    Remotes("Remotes & accessories", "android.settings.SETTINGS", note = "Opens TV settings. Choose Remotes & Accessories there."),
    Apps("Apps", "android.settings.MANAGE_APPLICATIONS_SETTINGS"),
    Storage("Storage", "android.settings.INTERNAL_STORAGE_SETTINGS"),
    DateTime("Date & time", null, "com.android.tv.settings/.system.DateTimeActivity"),
    Language("Language", "android.settings.LOCALE_SETTINGS"),
    Keyboard("Keyboard", "android.settings.INPUT_METHOD_SETTINGS"),
    About("About this TV", "android.settings.DEVICE_INFO_SETTINGS"),
}

/** How the page is opened: in a fresh task, so Back comes straight back to the launcher. */
fun TvPage.intent(): Intent {
    val intent = if (action != null) Intent(action) else Intent().setComponent(ComponentName.unflattenFromString(component!!))
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}

/** Opens [page]; false when it could not be opened. */
fun Context.openTvPage(page: TvPage): Boolean = safeStart(page.intent())

/** Packages that answer a settings intent without showing a page: the system's chooser and Google's stubs. */
private val NOT_A_PAGE = setOf("android", "com.google.android.tv.frameworkpackagestubs")

/**
 * The pages this TV can open, in order. [resolvedPackage] is the package the system would start for an intent, or null
 * when nothing handles it. A row is never listed for a page that would not open.
 */
fun listedTvPages(resolvedPackage: (Intent) -> String?): List<TvPage> =
    TvPage.entries.filter { page -> resolvedPackage(page.intent()).let { it != null && it !in NOT_A_PAGE } }

/** What the Network & Internet row says about the connection. */
fun networkLabel(connected: Boolean, wifi: Boolean, ethernet: Boolean): String = when {
    !connected -> "Not connected"
    ethernet -> "Ethernet"
    wifi -> "Wi-Fi"
    else -> "Connected"
}

/** "1.2 GB free", "12 GB free", "850 MB free"; counted in thousands, as the TV's own Storage page does. Null when unknown. */
fun freeSpaceLabel(bytes: Long?): String? {
    if (bytes == null || bytes < 0) return null
    val mb = bytes / 1_000_000
    return when {
        mb < 1_000 -> "$mb MB free"
        mb < 10_000 -> "${mb / 1_000}.${mb % 1_000 / 100} GB free"
        else -> "${mb / 1_000} GB free"
    }
}

/** What Settings › TV settings shows: the pages that can be opened, and the two values it can read by itself. */
class TvFacts(val pages: List<TvPage>, val network: String, val storage: String?)

/** Reads [TvFacts] from the system. Anything that cannot be read is left out; nothing here needs a permission the app lacks. */
fun Context.readTvFacts(): TvFacts {
    val pages = listedTvPages { intent -> runCatching { packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName }.getOrNull() }
    val caps = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java)
        cm?.getNetworkCapabilities(cm.activeNetwork)
    }.getOrNull()
    val network = networkLabel(
        connected = caps != null,
        wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
    )
    val free = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrNull()
    return TvFacts(pages, network, freeSpaceLabel(free))
}
