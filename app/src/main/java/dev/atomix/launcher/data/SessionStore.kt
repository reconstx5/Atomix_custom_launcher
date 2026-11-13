package dev.atomix.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Everything the launcher remembers. The token lives in encrypted storage on the device. */
class SessionStore(context: Context, encrypted: Boolean = true) {
    private val prefs: SharedPreferences = if (encrypted) {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "atomix_session", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } else {
        context.getSharedPreferences("atomix_session_plain", Context.MODE_PRIVATE)
    }

    // Encrypted prefs decrypt on every read, and Home reads these per frame, so values are kept in memory.
    private val memo = HashMap<String, String?>()
    private fun str(key: String): String? = synchronized(memo) {
        if (memo.containsKey(key)) memo[key] else prefs.getString(key, null).also { memo[key] = it }
    }

    var serverUrl: HttpUrl?
        get() = str(K_SERVER)?.toHttpUrlOrNull()
        set(v) = put(K_SERVER, v?.toString())
    var lastKnownUrl: HttpUrl?
        get() = str(K_LAST)?.toHttpUrlOrNull()
        set(v) = put(K_LAST, v?.toString())
    var token: String?
        get() = str(K_TOKEN)
        set(v) = put(K_TOKEN, v)
    var profileId: Long?
        get() = synchronized(memo) {
            if (memo.containsKey(K_PROFILE)) memo[K_PROFILE]?.toLong()
            else (if (prefs.contains(K_PROFILE)) prefs.getLong(K_PROFILE, 0) else null).also { memo[K_PROFILE] = it?.toString() }
        }
        set(v) {
            synchronized(memo) { memo[K_PROFILE] = v?.toString() }
            prefs.edit().apply { if (v == null) remove(K_PROFILE) else putLong(K_PROFILE, v) }.apply()
        }
    var profileName: String?
        get() = str(K_PROFILE_NAME)
        set(v) = put(K_PROFILE_NAME, v)
    var cachedHomeJson: String?
        get() = str(K_HOME)
        set(v) = put(K_HOME, v)
    var dock: List<String>?
        get() = str(K_DOCK)?.let { s -> if (s.isEmpty()) emptyList() else s.split('\n') }
        set(v) = put(K_DOCK, v?.joinToString("\n"))

    fun clearAuth() {
        synchronized(memo) { listOf(K_TOKEN, K_PROFILE, K_PROFILE_NAME, K_HOME).forEach { memo[it] = null } }
        prefs.edit().remove(K_TOKEN).remove(K_PROFILE).remove(K_PROFILE_NAME).remove(K_HOME).apply()
    }

    private fun put(key: String, v: String?) {
        synchronized(memo) { memo[key] = v }
        prefs.edit().apply { if (v == null) remove(key) else putString(key, v) }.apply()
    }

    private companion object {
        const val K_SERVER = "server"
        const val K_LAST = "last_known"
        const val K_TOKEN = "token"
        const val K_PROFILE = "profile_id"
        const val K_PROFILE_NAME = "profile_name"
        const val K_HOME = "home_cache"
        const val K_DOCK = "dock"
    }
}
