package dev.atomix.launcher.atomix

import android.content.Context
import android.content.Intent
import dev.atomix.launcher.apps.ATOMIX_ACTIVITY

const val EXTRA_ROUTE = "route"

/** Atomix's own Live TV source setup page (admin). */
const val ROUTE_LIVE_SETTINGS = "#/settings/live"

/** Opens the Atomix app, optionally at one of its `#/` routes. */
fun atomixIntent(context: Context, route: String? = null): Intent =
    Intent(Intent.ACTION_MAIN).setClassName(context, ATOMIX_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply { if (route != null && route.startsWith("#/")) putExtra(EXTRA_ROUTE, route) }
