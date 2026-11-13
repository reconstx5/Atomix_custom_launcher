package dev.atomix.launcher.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.atomix.launcher.HomeActivity

/**
 * This TV's firmware keeps the Google TV launcher as Home (higher intent priority, re-enabled at boot), so
 * Atomix can't be picked as Home. Instead, when the stock home screen comes to the front, open Atomix.
 * The service only receives window-state events from the stock launcher package and never reads window content
 * (see res/xml/home_override_service.xml). The owner turns it on in Settings > Accessibility.
 */
object HomeOverride {
    const val STOCK_PACKAGE = "com.google.android.tvlauncher"
    const val STOCK_HOME = "com.google.android.tvlauncher.MainActivity"

    fun shouldRedirect(eventType: Int, pkg: CharSequence?, cls: CharSequence?): Boolean =
        eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg?.toString() == STOCK_PACKAGE &&
            cls?.toString() == STOCK_HOME
}

class HomeOverrideService : AccessibilityService() {
    private var lastRedirect = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!HomeOverride.shouldRedirect(event.eventType, event.packageName, event.className)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 500) return
        lastRedirect = now
        startActivity(
            Intent(this, HomeActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    override fun onInterrupt() {}
}
