package dev.atomix.launcher.livetv.source

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

/** In-memory Live TV server for tests. Favourites are ordered ids; set [down] to simulate an unreachable server. */
class FakeLiveClient(
    var channels: List<Channel> = emptyList(),
    var favourites: MutableList<String> = mutableListOf(),
    var enabled: Boolean = true,
) : LiveClient {
    var down = false
    var failWrites = false
    var lastOrder: List<String>? = null
    var refreshes = 0
    val calls = mutableListOf<String>()
    var playReplies = ArrayDeque<() -> PlayReply>()
    var pingResult: () -> Boolean = { true }
    /** What /api/live/status answers; null means enabled, no sources, admin. */
    var statusReply: LiveStatus? = null
    /** Thrown by [refreshSource] (e.g. a 403 for a non-admin profile). */
    var refreshFailure: Exception? = null

    private fun check() { if (down) throw IOException("down") }
    private fun checkWrite() { check(); if (failWrites) throw IOException("write failed") }

    override suspend fun status() = check().let { refreshes++; statusReply ?: LiveStatus(enabled, emptyList(), admin = true) }
    override suspend fun channels(): List<Channel> { check(); return channels.map { it.copy(favourite = it.id in favourites) } }
    override suspend fun channelsJson(): String = buildJsonArray {
        channels().forEach { c ->
            add(buildJsonObject {
                put("id", c.id); c.number?.let { put("number", it) }; put("name", c.name); c.logo?.let { put("logo", it) }
                put("radio", c.radio); put("favourite", c.favourite)
                c.now?.let { p -> put("now", prog(p)) }; c.next?.let { p -> put("next", prog(p)) }
            })
        }
    }.toString()
    private fun prog(p: Programme) = buildJsonObject { put("title", p.title); put("start", p.start); put("end", p.end) }
    override suspend fun favouriteIds(): List<String> { check(); return favourites.filter { id -> channels.any { it.id == id } } }
    override suspend fun addFavourite(id: String) { checkWrite(); calls += "add $id"; if (id !in favourites) favourites += id }
    override suspend fun removeFavourite(id: String) { checkWrite(); calls += "remove $id"; favourites.remove(id) }
    override suspend fun setFavouriteOrder(ids: List<String>): List<String> {
        checkWrite(); lastOrder = ids; favourites = ids.toMutableList(); return ids
    }
    override suspend fun play(id: String, caps: PlayCaps, replaces: String?): PlayReply {
        check(); calls += "play $id replaces=$replaces"; return playReplies.removeFirst()()
    }
    override suspend fun ping(reply: PlayReply): Boolean { check(); calls += "ping ${reply.sessionId}"; return pingResult() }
    override suspend fun stop(reply: PlayReply) { calls += "stop ${reply.sessionId}" }
    override suspend fun refreshSource(id: String) { checkWrite(); refreshFailure?.let { throw it }; calls += "refresh $id" }
    override fun url(path: String?): HttpUrl? = path?.let { "http://server$it".toHttpUrl() }
}

fun tv(id: String, number: Int? = null, now: Programme? = null, next: Programme? = null) =
    Channel(id = id, number = number, name = "Ch $id", now = now, next = next)

fun radio(id: String) = Channel(id = id, name = "Radio $id", radio = true)
