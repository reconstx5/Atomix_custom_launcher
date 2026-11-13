package dev.atomix.launcher.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

object AtomixJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
}
