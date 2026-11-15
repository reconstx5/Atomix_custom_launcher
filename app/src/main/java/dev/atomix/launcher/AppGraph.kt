package dev.atomix.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.InstalledApps
import dev.atomix.launcher.apps.TvInputs
import dev.atomix.launcher.data.AtomixApi
import dev.atomix.launcher.data.AtomixClient
import dev.atomix.launcher.data.AtomixHttp
import dev.atomix.launcher.livetv.source.LiveApi
import dev.atomix.launcher.livetv.source.LiveClient
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.data.OkHttpServerProbe
import dev.atomix.launcher.data.ServerLocator
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.data.subnetHostsOf
import dev.atomix.launcher.prefs.HiddenApps
import dev.atomix.launcher.prefs.LauncherPrefs
import dev.atomix.launcher.prefs.RowStore
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val tvInputs = TvInputs(context)
    val prefs = LauncherPrefs(context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)) { store.dock }
    /** Lives as long as the process; for loads that outlast any one screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val apps = AppCatalog(installedApps::list, scope)
    val hiddenApps = HiddenApps(prefs)
    val rows = RowStore(prefs)

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = apps.invalidate()
        }, filter)
    }

    fun apiFor(url: HttpUrl): AtomixClient = AtomixApi(url, { store.token }, http)
    fun api(): AtomixClient? = store.serverUrl?.let(::apiFor)
    /** Starting a channel can take 10 s or more while the server tunes or starts a remux. */
    private val liveHttp: OkHttpClient by lazy { http.newBuilder().readTimeout(30, TimeUnit.SECONDS).build() }
    fun liveClient(): LiveClient? = store.serverUrl?.let { LiveApi(AtomixHttp(it, { store.token }, liveHttp)) }

    /** Channels and pins; when the server stops answering it is looked for again (its address may have changed). */
    val live = LiveRepository(::liveClient, store, relocate = {
        locator.locate(store.serverUrl, store.lastKnownUrl)?.let { found ->
            if (found != store.serverUrl) { store.lastKnownUrl = store.serverUrl; store.serverUrl = found }
        }
    })

    /** HTTP that adds the Atomix token only on requests to the Atomix server (images, video, DRM licences). */
    val authedHttp: OkHttpClient by lazy {
        http.newBuilder().addInterceptor { chain ->
            val req = chain.request()
            val server = store.serverUrl
            val token = store.token
            if (server != null && token != null && req.url.host == server.host && req.url.port == server.port) {
                chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
            } else {
                chain.proceed(req)
            }
        }.readTimeout(20, TimeUnit.SECONDS).build()
    }

    /** Coil loader over [authedHttp]; small caches for a 1.4 GB TV. */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .okHttpClient(authedHttp)
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
