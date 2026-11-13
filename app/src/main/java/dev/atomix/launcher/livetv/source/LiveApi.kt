package dev.atomix.launcher.livetv.source

import dev.atomix.launcher.data.AtomixHttp
import dev.atomix.launcher.livetv.source.LiveParse.strings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import java.net.URLEncoder

/** Atomix's Live TV API, as the launcher uses it. */
interface LiveClient {
    suspend fun status(): LiveStatus
    suspend fun channels(): List<Channel>
    /** Raw channel list, for caching as-is. */
    suspend fun channelsJson(): String
    /** The profile's favourite channel ids, in their saved order. */
    suspend fun favouriteIds(): List<String>
    suspend fun addFavourite(id: String)
    suspend fun removeFavourite(id: String)
    /** Sets the profile's favourite order; favourites missing from [ids] are removed by the server. */
    suspend fun setFavouriteOrder(ids: List<String>): List<String>
    suspend fun play(id: String, caps: PlayCaps, replaces: String?): PlayReply
    /** False when the server says the session is gone; throws on network errors. */
    suspend fun ping(reply: PlayReply): Boolean
    suspend fun stop(reply: PlayReply)
    suspend fun refreshSource(id: String)
    fun url(path: String?): HttpUrl?
}

fun parseChannels(json: String): List<Channel> =
    (Json.parseToJsonElement(json) as? JsonArray).orEmpty().mapNotNull(LiveParse::channel)

class LiveApi(private val http: AtomixHttp) : LiveClient {
    private fun enc(id: String) = URLEncoder.encode(id, "UTF-8").replace("+", "%20")

    override suspend fun status(): LiveStatus = LiveParse.status(Json.parseToJsonElement(http.get("/api/live/status").second).jsonObject)

    override suspend fun channels(): List<Channel> = parseChannels(channelsJson())

    override suspend fun channelsJson(): String = http.get("/api/live/channels").second

    override suspend fun favouriteIds(): List<String> = parseChannels(http.get("/api/live/channels?favourites=1").second).map { it.id }

    override suspend fun addFavourite(id: String) { http.post("/api/live/favourites/${enc(id)}") }

    override suspend fun removeFavourite(id: String) { http.delete("/api/live/favourites/${enc(id)}") }

    override suspend fun setFavouriteOrder(ids: List<String>): List<String> {
        val body = buildJsonObject { putJsonArray("channelIds") { ids.forEach { add(JsonPrimitive(it)) } } }.toString()
        val reply = Json.parseToJsonElement(http.put("/api/live/favourites", body).second) as? JsonObject
        return reply?.strings("channelIds") ?: ids
    }

    override suspend fun play(id: String, caps: PlayCaps, replaces: String?): PlayReply {
        val body = buildJsonObject {
            putJsonObject("caps") {
                putJsonArray("video") { caps.video.forEach { add(JsonPrimitive(it)) } }
                putJsonArray("audio") { caps.audio.forEach { add(JsonPrimitive(it)) } }
                putJsonArray("containers") { caps.containers.forEach { add(JsonPrimitive(it)) } }
                put("hls", caps.hls)
                put("hdr", caps.hdr)
                put("mse", caps.mse)
            }
            if (replaces != null) put("replaces", replaces)
        }.toString()
        return LiveParse.playReply(Json.parseToJsonElement(http.post("/api/live/channels/${enc(id)}/play", body).second).jsonObject)
    }

    override suspend fun ping(reply: PlayReply): Boolean {
        val o = Json.parseToJsonElement(http.post(reply.pingPath).second.ifBlank { "{}" }) as? JsonObject
        return (o?.get("ok") as? JsonPrimitive)?.booleanOrNull != false
    }

    override suspend fun stop(reply: PlayReply) { http.post(reply.stopPath) }

    override suspend fun refreshSource(id: String) { http.post("/api/live/sources/${enc(id)}/refresh") }

    override fun url(path: String?): HttpUrl? = path?.takeIf { it.isNotBlank() }?.let { http.baseUrl.resolve(it) }
}
