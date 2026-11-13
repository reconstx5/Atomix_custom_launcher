package dev.atomix.launcher.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

/** What the launcher needs from an Atomix server. */
interface AtomixClient {
    suspend fun status(): Status
    suspend fun quickConnectStart(): QuickConnectStart
    suspend fun quickConnectPoll(poll: String): PollResult
    suspend fun profiles(): List<Profile>
    suspend fun selectProfile(id: Long, pin: String?)
    /** Raw /api/home body, so the launcher can cache it as-is. */
    suspend fun homeJson(): String
    suspend fun home(): HomeResponse = AtomixJson.parseHome(homeJson())
    fun imageUrl(path: String?): HttpUrl?
}

class AtomixApi(
    private val baseUrl: HttpUrl,
    token: () -> String?,
    client: OkHttpClient,
) : AtomixClient {
    private val http = AtomixHttp(baseUrl, token, client)

    override suspend fun status(): Status = AtomixJson.json.decodeFromString(http.get("/api/status").second)

    override suspend fun quickConnectStart(): QuickConnectStart =
        AtomixJson.json.decodeFromString(http.post("/api/quickconnect", "{}").second)

    override suspend fun quickConnectPoll(poll: String): PollResult {
        val body = buildJsonObject { put("poll", poll) }.toString()
        val (resp, text) = http.post("/api/quickconnect/poll", body)
        val status = (AtomixJson.json.parseToJsonElement(text).jsonObject["status"] as? JsonPrimitive)?.content
        return when (status) {
            "approved" -> {
                val cookie = resp.headers("Set-Cookie").firstNotNullOfOrNull { c ->
                    Regex("""(?:^|\s)(?:__Secure-)?atomix_session=([^;]+)""").find(c)?.groupValues?.get(1)
                }
                if (cookie != null) PollResult.Approved(cookie) else PollResult.Expired
            }
            "waiting", "pending" -> PollResult.Pending
            else -> PollResult.Expired
        }
    }

    override suspend fun profiles(): List<Profile> = AtomixJson.json.decodeFromString(http.get("/api/profiles").second)

    override suspend fun selectProfile(id: Long, pin: String?) {
        val body = buildJsonObject { if (pin != null) put("pin", pin) }.toString()
        http.post("/api/profiles/$id/select", body)
    }

    override suspend fun homeJson(): String = http.get("/api/home").second

    override fun imageUrl(path: String?): HttpUrl? = path?.takeIf { it.isNotBlank() }?.let { baseUrl.resolve(it) }
}

class HttpException(val code: Int, val body: String) : IOException("HTTP $code")
