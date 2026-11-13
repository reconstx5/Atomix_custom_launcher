package dev.atomix.launcher.apps

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Installed apps, loaded once off the main thread and reloaded only when packages change. */
class AppCatalog(
    private val load: () -> List<AppEntry>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    init { invalidate() }

    fun invalidate() {
        scope.launch {
            runCatching { withContext(io) { load() } }.onSuccess { _apps.value = it }
        }
    }

    fun byPackage(pkg: String): AppEntry? = _apps.value.firstOrNull { it.packageName == pkg }
}
