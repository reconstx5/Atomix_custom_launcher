package dev.atomix.launcher

import android.content.Context
import android.net.ConnectivityManager
import dev.atomix.launcher.data.AtomixApi
import dev.atomix.launcher.data.AtomixClient
import dev.atomix.launcher.data.OkHttpServerProbe
import dev.atomix.launcher.data.ServerLocator
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.data.subnetHostsOf
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

/** The few long-lived objects the app shares. */
class AppGraph private constructor(private val context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    val store = SessionStore(context)
    val locator = ServerLocator(OkHttpServerProbe(http)) { ownIpv4()?.let(::subnetHostsOf).orEmpty() }

    fun apiFor(url: HttpUrl): AtomixClient = AtomixApi(url, { store.token }, http)
    fun api(): AtomixClient? = store.serverUrl?.let(::apiFor)

    private fun ownIpv4(): String? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val link = cm.getLinkProperties(cm.activeNetwork) ?: return@runCatching null
        link.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress
    }.getOrNull()

    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) { instance ?: AppGraph(context.applicationContext).also { instance = it } }
    }
}
