package dev.atomix.launcher.livetv.source

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** A guide entry; [start]/[end] are epoch milliseconds. */
data class Programme(
    val id: String? = null,
    val title: String,
    val start: Long,
    val end: Long,
    val subtitle: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    val image: String? = null,
)

/** A Live TV channel as the signed-in profile sees it. [drm] = the channel is encrypted. */
data class Channel(
    val id: String,
    val number: Int? = null,
    val name: String,
    val logo: String? = null,
    val radio: Boolean = false,
    val drm: Boolean = false,
    val favourite: Boolean = false,
    val now: Programme? = null,
    val next: Programme? = null,
)

data class LiveSource(
    val id: String,
    val name: String,
    val channels: Int = 0,
    val lastOk: Long? = null,
    val error: String? = null,
    val warning: String? = null,
    val lastRefresh: Long? = null,
)

/** [admin]: the server sent admin-only fields, so this profile may refresh and manage sources. */
data class LiveStatus(val enabled: Boolean, val sources: List<LiveSource>, val admin: Boolean)

/** What the player is asked to play; each kind keeps its session alive under its own address. */
sealed interface PlayReply {
    val sessionId: String
    val channel: Channel?
    val programme: Programme?
    val next: Programme?
    val pingPath: String
    val stopPath: String

    /** The tuner's 30-minute buffer as DASH. */
    data class Dash(
        override val sessionId: String,
        override val channel: Channel?,
        override val programme: Programme?,
        override val next: Programme?,
        val manifest: String,
        val rewind: Boolean,
    ) : PlayReply {
        override val pingPath get() = "/api/live/tuner/$sessionId/ping"
        override val stopPath get() = "/api/live/tuner/$sessionId/stop"
    }

    /** A converted stream (HLS or progressive). */
    data class Stream(
        override val sessionId: String,
        override val channel: Channel?,
        override val programme: Programme?,
        override val next: Programme?,
        val url: String,
        val delivery: String,
    ) : PlayReply {
        override val pingPath get() = "/api/playback/$sessionId/ping"
        override val stopPath get() = "/api/playback/$sessionId/stop"
    }

    /** An encrypted channel: DASH plus a licence address. */
    data class Drm(
        override val sessionId: String,
        override val channel: Channel?,
        override val programme: Programme?,
        override val next: Programme?,
        val manifest: String,
        val licence: String,
        val keySystem: String,
    ) : PlayReply {
        override val pingPath get() = "/api/live/drm/$sessionId/ping"
        override val stopPath get() = "/api/live/drm/$sessionId/stop"
    }
}

data class PlayCaps(
    val video: List<String>,
    val audio: List<String>,
    val containers: List<String>,
    val hls: Boolean,
    val hdr: Boolean = false,
    val mse: Boolean = false,
)

/** Lenient readers: the server sends numeric ids, `drm` as a list or null, and leaves fields out. */
internal object LiveParse {
    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }
    fun JsonObject.str(key: String): String? = prim(key)?.content
    fun JsonObject.long(key: String): Long? = prim(key)?.longOrNull
    fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull
    fun JsonObject.bool(key: String): Boolean = prim(key)?.booleanOrNull ?: false
    fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content }.orEmpty()

    fun programme(el: JsonElement?): Programme? {
        val o = el as? JsonObject ?: return null
        return Programme(
            id = o.str("id"),
            title = o.str("title") ?: return null,
            start = o.long("start") ?: return null,
            end = o.long("end") ?: return null,
            subtitle = o.str("subtitle"),
            description = o.str("description"),
            categories = o.strings("categories"),
            image = o.str("image"),
        )
    }

    fun channel(el: JsonElement?): Channel? {
        val o = el as? JsonObject ?: return null
        val drm = when (val d = o["drm"]) {
            is JsonArray -> d.isNotEmpty()
            is JsonPrimitive -> d.booleanOrNull ?: (d !is JsonNull && d.content.isNotEmpty())
            else -> false
        }
        return Channel(
            id = o.str("id") ?: return null,
            number = o.int("number"),
            name = o.str("name") ?: return null,
            logo = o.str("logo"),
            radio = o.bool("radio"),
            drm = drm,
            favourite = o.bool("favourite"),
            now = programme(o["now"]),
            next = programme(o["next"]),
        )
    }

    fun status(o: JsonObject): LiveStatus {
        val sources = (o["sources"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return LiveStatus(
            enabled = o.bool("enabled"),
            sources = sources.mapNotNull { s ->
                LiveSource(
                    id = s.str("id") ?: return@mapNotNull null,
                    name = s.str("name") ?: s.str("id")!!,
                    channels = s.int("channels") ?: 0,
                    lastOk = s.long("lastOk"),
                    error = s.str("error"),
                    warning = s.str("warning"),
                    lastRefresh = s.long("lastRefresh"),
                )
            },
            admin = sources.any { "lastRefresh" in it || "error" in it },
        )
    }

    fun playReply(o: JsonObject): PlayReply {
        val sid = o.str("sessionId") ?: error("No session in play reply")
        val ch = channel(o["channel"]) ?: (o["channel"] as? JsonObject)?.let { c ->
            c.str("id")?.let { id -> Channel(id = id, name = c.str("name") ?: "") }
        }
        val programme = programme(o["programme"])
        val next = programme(o["next"])
        return when (o.str("delivery")) {
            "dash" -> PlayReply.Dash(sid, ch, programme, next, o.str("manifest") ?: error("No manifest"), o.bool("rewind"))
            "drm" -> PlayReply.Drm(
                sid, ch, programme, next,
                manifest = o.str("manifest") ?: error("No manifest"),
                licence = o.str("licence") ?: error("No licence"),
                keySystem = o.str("keySystem") ?: "com.widevine.alpha",
            )
            else -> PlayReply.Stream(sid, ch, programme, next, o.str("url") ?: error("No stream address"), o.str("delivery") ?: "progressive")
        }
    }
}
