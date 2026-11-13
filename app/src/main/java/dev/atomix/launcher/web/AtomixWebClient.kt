package dev.atomix.launcher.web

import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Keeps the Atomix screen on the Atomix server (same scheme, host and port), reports a page that can't load
 * (server off or unreachable) and survives a WebView renderer crash instead of taking the launcher process down.
 */
class AtomixWebClient(
    origin: String,
    private val onOffline: () -> Unit,
    private val onRendererGone: () -> Unit,
) : WebViewClient() {
    private val home = Uri.parse(origin)

    private fun sameOrigin(url: Uri): Boolean =
        url.scheme == home.scheme && url.host == home.host && effectivePort(url) == effectivePort(home)

    private fun effectivePort(u: Uri): Int = if (u.port != -1) u.port else if (u.scheme == "https") 443 else 80

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !sameOrigin(request.url)

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onOffline()
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }
}
