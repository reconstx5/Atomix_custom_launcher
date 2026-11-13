package dev.atomix.launcher

import android.content.Intent
import android.os.Bundle
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
    val vm = HomeViewModel(graph::api, graph.store, graph.dockStore::dock, viewModelScope)
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
        val actions = HomeActions(
            openWeb = { route -> launch(Intent().setClassName(this, WEB_ACTIVITY).putExtra(EXTRA_ROUTE, route)) },
            openApp = { launch(it.launch) },
            openApps = { /* Apps grid arrives with Task 8. */ },
            openLiveTv = { liveTvIntent(packageManager)?.let(::launch) },
            listInputs = { TvInputs(this).hdmi() },
            openInput = { launch(TvInputs(this).intentFor(it)) },
            switchProfile = {},
        )
        setContent {
            AtomixTheme {
                var signedIn by remember { mutableStateOf(graph.store.token != null && graph.store.profileId != null) }
                if (!signedIn) {
                    val holder = viewModel { SetupHolder(graph) }
                    LaunchedEffect(Unit) { holder.vm.start() }
                    SetupFlow(holder.vm) { signedIn = true; home.vm.refresh() }
                } else {
                    val state by home.vm.state.collectAsState()
                    LaunchedEffect(state.status) { if (state.status == HomeState.Status.SignedOut) signedIn = false }
                    HomeScreen(
                        vm = home.vm,
                        loader = graph.imageLoader,
                        imageUrl = { path -> graph.api()?.imageUrl(path)?.toString() },
                        actions = HomeActions(
                            openWeb = actions.openWeb, openApp = actions.openApp, openApps = actions.openApps,
                            openLiveTv = actions.openLiveTv, listInputs = actions.listInputs, openInput = actions.openInput,
                            switchProfile = { graph.store.profileId = null; signedIn = false },
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (graph.store.token != null && graph.store.profileId != null) home.vm.refresh()
    }

    private fun launch(intent: Intent) {
        if (!safeStart(intent)) Toast.makeText(this, "Couldn't open that", Toast.LENGTH_SHORT).show()
    }
}
