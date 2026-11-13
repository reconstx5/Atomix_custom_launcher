package dev.atomix.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

const val ATOMIX_PORT = 8787

interface ServerProbe {
    suspend fun isAtomix(base: HttpUrl): Boolean
}

class OkHttpServerProbe(client: OkHttpClient) : ServerProbe {
    private val quick = client.newBuilder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun isAtomix(base: HttpUrl): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            quick.newCall(Request.Builder().url(base.resolve("/api/status")!!).build()).execute().use { r ->
                if (!r.isSuccessful) return@use false
                val o = AtomixJson.json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                (o["name"] as? JsonPrimitive)?.content == "Atomix"
            }
        }.getOrDefault(false)
    }
}

class ServerLocator(
    private val probe: ServerProbe,
    private val subnetHosts: () -> List<String>,
) {
    /** Saved address, then the last one that worked, then a sweep of the TV's /24 on port 8787. */
    suspend fun locate(saved: HttpUrl?, lastKnown: HttpUrl?): HttpUrl? {
        for (candidate in listOfNotNull(saved, lastKnown).distinct()) {
            if (probe.isAtomix(candidate)) return candidate
        }
        val gate = Semaphore(32)
        val found = coroutineScope {
            subnetHosts().map { host ->
                async {
                    val url = HttpUrl.Builder().scheme("http").host(host).port(ATOMIX_PORT).build()
                    gate.withPermit { if (probe.isAtomix(url)) url else null }
                }
            }.awaitAll()
        }
        return found.firstOrNull { it != null }
    }
}

/** Every host of the /24 that [ipv4] sits in, except [ipv4] itself. */
fun subnetHostsOf(ipv4: String): List<String> {
    val prefix = ipv4.substringBeforeLast('.')
    return (1..254).map { "$prefix.$it" }.filter { it != ipv4 }
}
