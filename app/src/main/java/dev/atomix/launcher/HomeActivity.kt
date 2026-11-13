package dev.atomix.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.atomix.launcher.ui.setup.SetupFlow
import dev.atomix.launcher.ui.setup.SetupViewModel
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixTheme
import dev.atomix.launcher.ui.theme.AtomixType

class SetupHolder(graph: AppGraph) : ViewModel() {
    val vm = SetupViewModel(graph::apiFor, graph.locator, graph.store, viewModelScope)
}

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(this)
        setContent {
            AtomixTheme {
                var signedIn by remember { mutableStateOf(graph.store.token != null && graph.store.profileId != null) }
                if (!signedIn) {
                    val holder = viewModel { SetupHolder(graph) }
                    androidx.compose.runtime.LaunchedEffect(Unit) { holder.vm.start() }
                    SetupFlow(holder.vm) { signedIn = true }
                } else {
                    Box(Modifier.fillMaxSize().background(AtomixColors.DeepSpace), contentAlignment = Alignment.Center) {
                        BasicText("Signed in as ${graph.store.profileName}", style = AtomixType.headline())
                    }
                }
            }
        }
    }
}
