package dev.atomix.launcher.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

const val LIVE_TV_PACKAGE = "com.mediatek.wwtv.tvcenter"

fun liveTvIntent(pm: PackageManager): Intent? =
    (pm.getLeanbackLaunchIntentForPackage(LIVE_TV_PACKAGE) ?: pm.getLaunchIntentForPackage(LIVE_TV_PACKAGE))
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun Context.safeStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

/**
 * Opens the TV's own settings at their main page, in a fresh task so Back comes straight back to the launcher; false
 * when it cannot be opened.
 *
 * This is how the owner reaches the accessibility page that holds the Home-button service. There is no way to open
 * that page directly on this TV: Settings.ACTION_ACCESSIBILITY_SETTINGS goes to a Google stub, and the TV settings
 * app's own android.settings.ACCESSIBILITY_TV_OEM_LINK starts and shows nothing. Both "succeed", so neither can be
 * tried first with this as a fallback.
 */
fun Context.openTvSettings(): Boolean =
    safeStart(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
