package dev.atomix.launcher.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

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
