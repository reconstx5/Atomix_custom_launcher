package dev.atomix.launcher.web

/** "#/play/9" from "http://host:8787/#/play/9" (trailing slashes ignored). */
fun hashRoute(url: String): String = "#" + url.substringAfter('#', "").trimEnd('/')

/**
 * Back closes the web screen when there is no page to go back to, or when the page showing is the one
 * Home opened (the root); otherwise Back walks Atomix's own history.
 */
fun shouldClose(rootRoute: String, currentUrl: String, canGoBack: Boolean): Boolean =
    !canGoBack || hashRoute(currentUrl) == "#" + rootRoute.removePrefix("#").trimEnd('/')
