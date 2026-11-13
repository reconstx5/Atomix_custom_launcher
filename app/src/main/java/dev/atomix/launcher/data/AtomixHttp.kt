package dev.atomix.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Calls to the Atomix server with the sign-in token; 401 means the sign-in expired. */
class AtomixHttp(val baseUrl: HttpUrl, private val token: () -> String?, private val client: OkHttpClient) {
    private val jsonType = "application/json".toMediaType()

    suspend fun get(path: String) = call(Request.Builder().url(resolve(path)).get())
    suspend fun post(path: String, json: String = "{}") = call(Request.Builder().url(resolve(path)).post(json.toRequestBody(jsonType)))
    suspend fun put(path: String, json: String) = call(Request.Builder().url(resolve(path)).put(json.toRequestBody(jsonType)))
    /** Atomix answers 415 to a DELETE without a JSON body, so it sends an empty one. */
    suspend fun delete(path: String) = call(Request.Builder().url(resolve(path)).delete("{}".toRequestBody(jsonType)))

    fun resolve(path: String): HttpUrl = baseUrl.resolve(path) ?: error("Bad path $path")

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
