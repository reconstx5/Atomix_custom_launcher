package dev.atomix.launcher

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.atomix.launcher.apps.TvInputs
import dev.atomix.launcher.apps.liveTvIntent
import dev.atomix.launcher.apps.safeStart
import dev.atomix.launcher.ui.apps.AppsScreen
import dev.atomix.launcher.ui.apps.AppsScreenState
import dev.atomix.launcher.ui.home.HomeActions
import dev.atomix.launcher.ui.home.HomeScreen
import dev.atomix.launcher.ui.home.HomeState
import dev.atomix.launcher.ui.home.HomeViewModel
import dev.atomix.launcher.ui.setup.SetupFlow
import dev.atomix.launcher.ui.setup.SetupViewModel
import dev.atomix.launcher.ui.theme.AtomixTheme

class SetupHolder(graph: AppGraph) : ViewModel() {
    val vm = SetupViewModel(graph::apiFor, graph.locator, graph.store, viewModelScope)
}

class HomeHolder(graph: AppGraph) : ViewModel() {
    val vm = HomeViewModel(graph::api, graph.store, graph.favouriteApps::visible, viewModelScope)
}

const val WEB_ACTIVITY = "dev.atomix.launcher.web.WebScreenActivity"
const val EXTRA_ROUTE = "route"

class HomeActivity : ComponentActivity() {
    private lateinit var graph: AppGraph
    private val home: HomeHolder by lazy {
        ViewModelProvider(this, viewModelFactory { initializer { HomeHolder(graph) } })[HomeHolder::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = AppGraph.get(this)
        val appsState = AppsScreenState(graph.favouriteApps) { graph.apps.apps.value }
        lifecycleScope.launch { graph.apps.apps.collect { home.vm.refreshDock() } }
        setContent {
            AtomixTheme {
                var signedIn by remember { mutableStateOf(graph.store.token != null && graph.store.profileId != null) }
                if (!signedIn) {
                    val holder = viewModel { SetupHolder(graph) }
                    LaunchedEffect(Unit) { holder.vm.start() }
                    SetupFlow(holder.vm) { signedIn = true; home.vm.refresh() }
                } else if (showApps.value) {
                    AppsScreen(
                        state = appsState,
                        loader = graph.imageLoader,
                        onLaunch = { launch(it.launch) },
                        onDockChanged = { home.vm.refreshDock() },
                        onBack = { showApps.value = false },
                    )
                } else {
                    val state by home.vm.state.collectAsState()
                    LaunchedEffect(state.status) { if (state.status == HomeState.Status.SignedOut) signedIn = false }
                    HomeScreen(
                        vm = home.vm,
                        loader = graph.imageLoader,
                        imageUrl = { path -> graph.api()?.imageUrl(path)?.toString() },
                        actions = HomeActions(
                            openWeb = { route -> launch(Intent().setClassName(this, WEB_ACTIVITY).putExtra(EXTRA_ROUTE, route)) },
                            openApp = { launch(it.launch) },
                            openApps = { showApps.value = true },
                            openLiveTv = { liveTvIntent(packageManager)?.let(::launch) ?: toast("Live TV isn't installed") },
                            listInputs = { TvInputs(this).hdmi() },
                            openInput = { launch(TvInputs(this).intentFor(it)) },
                            switchProfile = { graph.store.profileId = null; signedIn = false },
                            moveDockApp = { pkg, delta -> graph.favouriteApps.let { f -> f.move(pkg, f.visible().indexOfFirst { it.packageName == pkg } + delta) } },
                            removeDockApp = { graph.favouriteApps.remove(it) },
                        ),
                    )
                }
            }
        }
    }

    /** Pressing Home while the Apps grid is open returns to the home screen. */
    private val showApps = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showApps.value = false
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        if (graph.store.token != null && graph.store.profileId != null) home.vm.refresh()
    }

    private fun launch(intent: Intent) {
        if (!safeStart(intent)) toast("Couldn't open that")
    }
}
