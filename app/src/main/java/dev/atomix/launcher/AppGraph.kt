package dev.atomix.launcher

import android.content.Context
import android.net.ConnectivityManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.atomix.launcher.apps.InstalledApps
import dev.atomix.launcher.data.AtomixApi
import dev.atomix.launcher.data.AtomixClient
import dev.atomix.launcher.data.OkHttpServerProbe
import dev.atomix.launcher.data.ServerLocator
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.data.subnetHostsOf
import dev.atomix.launcher.prefs.FavouriteApps
import dev.atomix.launcher.prefs.LauncherPrefs
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
    val installedApps = InstalledApps(context.packageManager)
    val prefs = LauncherPrefs(context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)) { store.dock }
    val favouriteApps = FavouriteApps(prefs) { installedApps.list() }

    fun apiFor(url: HttpUrl): AtomixClient = AtomixApi(url, { store.token }, http)
    fun api(): AtomixClient? = store.serverUrl?.let(::apiFor)

    /** Coil loader that sends the Atomix token only to the Atomix server; small caches for a 1.4 GB TV. */
    val imageLoader: ImageLoader by lazy {
        val authed = http.newBuilder().addInterceptor { chain ->
            val req = chain.request()
            val server = store.serverUrl
            val token = store.token
            if (server != null && token != null && req.url.host == server.host && req.url.port == server.port) {
                chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
            } else {
                chain.proceed(req)
            }
        }.build()
        ImageLoader.Builder(context)
            .okHttpClient(authed)
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.15).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(64L * 1024 * 1024).build() }
            .allowRgb565(true)
            .build()
    }

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
