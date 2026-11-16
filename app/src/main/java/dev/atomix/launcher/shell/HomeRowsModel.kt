package dev.atomix.launcher.shell

/** The drawn row over [id], or null when it is the first (Up then goes to the top bar) or not drawn. */
fun rowAbove(id: String, drawn: List<String>): String? = drawn.indexOf(id).takeIf { it > 0 }?.let { drawn[it - 1] }

/** The drawn row under [id], or null when it is the last (Down then goes nowhere) or not drawn. */
fun rowBelow(id: String, drawn: List<String>): String? = drawn.indexOf(id).takeIf { it >= 0 }?.let { drawn.getOrNull(it + 1) }
