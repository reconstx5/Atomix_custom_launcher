package dev.atomix.launcher.livetv.player

import dev.atomix.launcher.livetv.source.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Typing a channel number on the remote: up to 4 digits, committed [commitMs] after the last one. */
class NumberEntry(private val scope: CoroutineScope, private val commitMs: Long = 1_500, private val onCommit: (Int) -> Unit) {
    private val _typed = MutableStateFlow("")
    val typed: StateFlow<String> = _typed
    private var pending: Job? = null

    fun digit(d: Int) {
        if (d !in 0..9 || _typed.value.length >= MAX_DIGITS) return
        _typed.value += d.toString()
        pending?.cancel()
        pending = scope.launch {
            delay(commitMs)
            val n = _typed.value.toIntOrNull()
            _typed.value = ""
            if (n != null) onCommit(n)
        }
    }

    fun clear() { pending?.cancel(); pending = null; _typed.value = "" }

    private companion object { const val MAX_DIGITS = 4 }
}

fun findByNumber(list: List<Channel>, n: Int): Channel? = list.firstOrNull { it.number == n }

/**
 * The channel the digits typed so far name, if any ("02" is channel 2). Nothing for no digits, or digits that match no
 * channel: the chip then shows the digits alone.
 */
fun numberMatch(typed: String, all: List<Channel>): Channel? = typed.toIntOrNull()?.let { findByNumber(all, it) }
