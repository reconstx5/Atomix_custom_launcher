package dev.atomix.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class AtomixApi(
    private val baseUrl: HttpUrl,
    private val token: () -> String?,
    private val client: OkHttpClient,
) {
    private val jsonType = "application/json".toMediaType()

    suspend fun status(): Status = AtomixJson.json.decodeFromString(get("/api/status").second)

    suspend fun quickConnectStart(): QuickConnectStart =
        AtomixJson.json.decodeFromString(post("/api/quickconnect", "{}").second)

    suspend fun quickConnectPoll(poll: String): PollResult {
        val body = buildJsonObject { put("poll", poll) }.toString()
        val (resp, text) = post("/api/quickconnect/poll", body)
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

    suspend fun profiles(): List<Profile> = AtomixJson.json.decodeFromString(get("/api/profiles").second)

    suspend fun selectProfile(id: Long, pin: String?) {
        val body = buildJsonObject { if (pin != null) put("pin", pin) }.toString()
        post("/api/profiles/$id/select", body)
    }

    suspend fun home(): HomeResponse = AtomixJson.parseHome(get("/api/home").second)

    fun imageUrl(path: String?): HttpUrl? = path?.takeIf { it.isNotBlank() }?.let { baseUrl.resolve(it) }

    private suspend fun get(path: String) = call(Request.Builder().url(baseUrl.resolve(path)!!).get())

    private suspend fun post(path: String, json: String) =
        call(Request.Builder().url(baseUrl.resolve(path)!!).post(json.toRequestBody(jsonType)))

    private suspend fun call(builder: Request.Builder): Pair<Response, String> = withContext(Dispatchers.IO) {
        token()?.let { builder.header("Authorization", "Bearer $it") }
        builder.header("Accept", "application/json")
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            when {
                resp.code == 401 -> throw AuthExpiredException()
                !resp.isSuccessful -> throw HttpException(resp.code, text)
                else -> resp to text
            }
        }
    }
}

class HttpException(val code: Int, val body: String) : IOException("HTTP $code")
