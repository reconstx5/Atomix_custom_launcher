package dev.atomix.launcher.web

import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AtomixWebClientTest {
    private var offline = 0
    private var gone = 0
    private val client = AtomixWebClient("http://192.168.8.65:8787", onOffline = { offline++ }, onRendererGone = { gone++ })
    private val view by lazy { WebView(ApplicationProvider.getApplicationContext()) }

    private fun request(url: String, mainFrame: Boolean = true) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private val error: WebResourceError = android.webkit.FakeWebResourceError()

    @Test fun rendererCrash_isHandled_notFatal() {
        val handled = client.onRenderProcessGone(view, object : RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        })
        assertTrue(handled)
        assertEquals(1, gone)
    }

    @Test fun mainFrameLoadError_reportsOffline_subresourceErrorDoesNot() {
        client.onReceivedError(view, request("http://192.168.8.65:8787/#/play/1", mainFrame = false), error)
        assertEquals(0, offline)
        client.onReceivedError(view, request("http://192.168.8.65:8787/#/play/1"), error)
        assertEquals(1, offline)
    }

    @Test fun staysOnTheAtomixOrigin_exactly() {
        assertFalse(client.shouldOverrideUrlLoading(view, request("http://192.168.8.65:8787/#/item/5")))
        assertTrue(client.shouldOverrideUrlLoading(view, request("http://192.168.8.65:87870/x")))
        assertTrue(client.shouldOverrideUrlLoading(view, request("http://192.168.8.65:8787.evil.tld/x")))
        assertTrue(client.shouldOverrideUrlLoading(view, request("https://example.com/")))
    }
}
