package dev.atomix.launcher.web

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import android.widget.FrameLayout
import dev.atomix.launcher.AppGraph
import dev.atomix.launcher.EXTRA_ROUTE

/**
 * One full-screen WebView for Atomix's player, detail, library and search pages.
 * It exists only while this activity is open and is destroyed on exit, so Home never holds one.
 */
class WebScreenActivity : Activity() {
    private var webView: WebView? = null
    private lateinit var root: String
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val store = AppGraph.get(this).store
        val server = store.serverUrl
        val token = store.token
        if (server == null || token == null) { finish(); return }
        root = intent.getStringExtra(EXTRA_ROUTE)?.takeIf { it.startsWith("#/") } ?: "#/"
        val origin = server.toString().trimEnd('/')

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie(origin, "atomix_session=$token; Path=/")
            flush()
        }

        val container = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#05050C")) }
        val web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#05050C"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = settings.userAgentString + " AtomixLauncher/1"
            webViewClient = AtomixWebClient(
                origin,
                onOffline = { leave("Atomix isn't reachable right now") },
                onRendererGone = { leave("Atomix closed unexpectedly") },
            )
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val fromServer = request.origin.toString().trimEnd('/') == origin
                    val wanted = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                    if (fromServer && wanted.isNotEmpty()) request.grant(wanted.toTypedArray()) else request.deny()
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    customView = view; customCallback = callback
                    container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }

                override fun onHideCustomView() {
                    customView?.let(container::removeView)
                    customView = null
                    customCallback = null
                }
            }
        }
        container.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(container)
        webView = web
        web.loadUrl("$origin/$root")
        web.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val web = webView
        if (event.keyCode == KeyEvent.KEYCODE_BACK && web != null) {
            if (event.action == KeyEvent.ACTION_UP) {
                when {
                    customView != null -> customCallback?.onCustomViewHidden()
                    shouldClose(root, web.url.orEmpty(), web.canGoBack()) -> finish()
                    else -> web.goBack()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    /** Leaves the Atomix screen with a short note; the WebView is torn down in onDestroy. */
    private fun leave(message: String) {
        if (isFinishing) return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}
