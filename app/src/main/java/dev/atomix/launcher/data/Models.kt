package dev.atomix.launcher.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class Progress(val position: Double, val duration: Double? = null, val watched: Boolean = false)

@Serializable
data class Item(
    val id: Long,
    val kind: String,
    val title: String,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val runtime: Int? = null,
    val duration: Double? = null,
    val overview: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val tint: String? = null,
    val tintWide: String? = null,
    val progress: Progress? = null,
    val tagline: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

data class Row(val key: String, val id: String? = null, val title: String, val style: String? = null, val items: List<Item> = emptyList())

@Serializable
data class Library(val id: Long, val type: String? = null, val name: String)

data class HomeResponse(val hero: Item? = null, val rows: List<Row> = emptyList(), val libraries: List<Library> = emptyList())

@Serializable
data class Profile(val id: Long, val name: String, val kids: Boolean = false, val hasPin: Boolean = false, val avatar: String? = null)

@Serializable
data class Status(val name: String, val serverName: String? = null, val quickConnect: Boolean = false)

@Serializable
data class QuickConnectStart(val code: String, val poll: String, val expiresAt: Long, val ttlMs: Long)

sealed interface PollResult {
    data object Pending : PollResult
    data object Expired : PollResult
    data class Approved(val token: String) : PollResult
}

class AuthExpiredException : Exception("Atomix sign-in expired")

/** Lenient parsing: rows mix item shapes (live channels have string ids), so items are decoded one by one. */
object AtomixJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    fun parseHome(text: String): HomeResponse {
        val root = json.parseToJsonElement(text).jsonObject
        val hero = root["hero"]?.let(::decodeItem)
        val rows = (root["rows"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val key = (o["key"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            Row(
                key = key,
                id = (o["id"] as? JsonPrimitive)?.content,
                title = (o["title"] as? JsonPrimitive)?.content.orEmpty(),
                style = (o["style"] as? JsonPrimitive)?.content,
                items = (o["items"] as? JsonArray).orEmpty().mapNotNull(::decodeItem),
            )
        }
        val libraries = (root["libraries"] as? JsonArray).orEmpty().mapNotNull {
            runCatching { json.decodeFromJsonElement<Library>(it) }.getOrNull()
        }
        return HomeResponse(hero, rows, libraries)
    }

    private fun decodeItem(el: JsonElement): Item? {
        val o = el as? JsonObject ?: return null
        if ((o["id"] as? JsonPrimitive)?.isString != false) return null
        if (o["progress"] is JsonPrimitive && o["progress"].toString() != "null") return null
        return runCatching { json.decodeFromJsonElement<Item>(o) }.getOrNull()
    }
}
