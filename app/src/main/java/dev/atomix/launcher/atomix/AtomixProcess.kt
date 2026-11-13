package dev.atomix.launcher.atomix

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.core.content.getSystemService

/** The suffix Atomix's own process runs under (see android:process in the manifest). */
private const val ATOMIX_SUFFIX = ":atomix"

/**
 * The pid of Atomix's own process among [running], or null when it isn't up. [running] is the app's own process
 * list, as name to pid — since Android 5.1 that is all the system reports, so the package prefix is checked too.
 */
fun atomixPid(running: List<Pair<String, Int>>, packageName: String = "dev.atomix.launcher"): Int? =
    running.firstOrNull { (name, _) -> name == packageName + ATOMIX_SUFFIX }?.second

/**
 * Ends Atomix's own process. Its WebView keeps the sign-in in a cookie and reads the token once at start, and
 * SharedPreferences are not shared between processes — so after the token changes or the profile is switched, a
 * process still running would open Atomix as whoever was signed in before. Ending it makes the next open read the
 * new sign-in. It is left alone otherwise, so returning to Atomix is still instant.
 */
fun stopAtomixProcess(context: Context) {
    val am = context.getSystemService<ActivityManager>() ?: return
    val running = runCatching { am.runningAppProcesses.orEmpty().map { it.processName to it.pid } }.getOrNull().orEmpty()
    val pid = atomixPid(running, context.packageName) ?: return
    if (pid != Process.myPid()) runCatching { Process.killProcess(pid) }
}
