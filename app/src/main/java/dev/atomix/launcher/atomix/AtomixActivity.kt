package dev.atomix.launcher.atomix

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.web.AtomixWebClient
import dev.atomix.launcher.web.shouldClose

/**
 * The Atomix app: its web app full screen, in its own task and its own process (`:atomix`), so its WebView
 * never counts against Home's memory and pressing Home leaves it running like any other app.
 */
class AtomixActivity : Activity() {
    private var webView: WebView? = null
    private lateinit var root: String
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(this)
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
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }

                override fun onHideCustomView() {
                    customView?.let(container::removeView)
                    customView = null
                    customCallback = null
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        container.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(container)
        webView = web
        web.loadUrl("$origin/$root")
        web.requestFocus()
    }

    /** Opened again with a route (e.g. Settings → Manage sources): go there in the running page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val route = intent.getStringExtra(EXTRA_ROUTE)?.takeIf { it.startsWith("#/") } ?: return
        val server = SessionStore(this).serverUrl ?: return
        root = route
        webView?.loadUrl(server.toString().trimEnd('/') + "/" + route)
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
