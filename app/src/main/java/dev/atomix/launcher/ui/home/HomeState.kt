package dev.atomix.launcher.ui.home

import androidx.compose.ui.graphics.Color
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.data.AuthExpiredException
import dev.atomix.launcher.data.HomeResponse
import dev.atomix.launcher.data.Item
import dev.atomix.launcher.data.Row

data class HomeState(
    val status: Status = Status.Loading,
    val hero: Item? = null,
    val row: Row? = null,
    val dock: List<AppEntry> = emptyList(),
    val profileName: String? = null,
    /** First Atomix library, for the rail's Library entry. */
    val libraryId: Long? = null,
) {
    enum class Status { Loading, Online, Offline, SignedOut }
}

/** Combines the cached home with the latest fetch (null = still loading). */
fun reduce(cached: HomeResponse?, fresh: Result<HomeResponse>?): Pair<HomeState.Status, HomeResponse?> {
    if (fresh == null) return HomeState.Status.Loading to cached
    fresh.onSuccess { return HomeState.Status.Online to it }
    val error = fresh.exceptionOrNull()
    if (error is AuthExpiredException) return HomeState.Status.SignedOut to null
    return if (cached != null) HomeState.Status.Online to cached else HomeState.Status.Offline to null
}

/** Rows never shown on Home: live channels (Live TV has its own rail and dock entry). */
private val EXCLUDED_ROWS = setOf("live-now")

fun pickRow(rows: List<Row>): Row? =
    rows.firstOrNull { it.key == "continue" && it.items.isNotEmpty() }
        ?: rows.firstOrNull { it.key !in EXCLUDED_ROWS && it.items.isNotEmpty() && (it.style == "landscape" || it.style == "poster") }

fun pickHero(resp: HomeResponse?): Item? = resp?.hero ?: resp?.let { pickRow(it.rows)?.items?.firstOrNull() }

/** Atomix tints are "#rrggbb" (also accepts "rgb(r,g,b)"). */
fun parseTint(value: String?): Color? {
    val v = value?.trim() ?: return null
    Regex("""^#([0-9a-fA-F]{6})$""").find(v)?.let { return Color(0xFF000000 or it.groupValues[1].toLong(16)) }
    Regex("""^rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)$""").find(v)?.let { m ->
        val (r, g, b) = m.destructured
        return Color(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }
    return null
}
