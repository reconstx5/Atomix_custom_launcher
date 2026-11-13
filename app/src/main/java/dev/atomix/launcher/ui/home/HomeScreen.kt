package dev.atomix.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import coil.ImageLoader
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.apps.InputEntry
import dev.atomix.launcher.data.Item
import dev.atomix.launcher.ui.theme.px

/** Everything the home screen can ask the activity to do. */
class HomeActions(
    val openWeb: (route: String) -> Unit,
    val openApp: (AppEntry) -> Unit,
    val openApps: () -> Unit,
    val openLiveTv: () -> Unit,
    val listInputs: () -> List<InputEntry>,
    val openInput: (InputEntry) -> Unit,
    val switchProfile: () -> Unit,
)

@Composable
fun HomeScreen(vm: HomeViewModel, loader: ImageLoader, imageUrl: (String?) -> String?, actions: HomeActions) {
    val state by vm.state.collectAsState()
    val focused by vm.focusedItem.collectAsState()
    val offline = state.status == HomeState.Status.Offline
    // The hero follows the focused card; before any card has had focus it shows Atomix's own hero.
    val shown: Item? = if (offline) null else (focused?.takeIf { f -> state.row?.items?.any { it.id == f.id } == true } ?: state.hero)

    val playFocus = remember { FocusRequester() }
    val dockFocus = remember { FocusRequester() }
    var inputs by remember { mutableStateOf<List<InputEntry>?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }

    BackHandler { /* Home is the bottom of the stack: Back does nothing here. */ }

    LaunchedEffect(state.status, shown != null) {
        if (initialFocusDone || state.status == HomeState.Status.Loading && shown == null) return@LaunchedEffect
        initialFocusDone = runCatching { if (shown != null) playFocus.requestFocus() else dockFocus.requestFocus() }.isSuccess
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(shown, offline, imageUrl, loader)
        TopBar(state.profileName, onProfile = actions.switchProfile)
        Column(Modifier.padding(top = px(150))) {
            HeroInfo(
                item = shown,
                offline = offline,
                imageUrl = imageUrl,
                loader = loader,
                playFocus = playFocus,
                onPlay = { actions.openWeb("#/play/${it.id}") },
                onInfo = { actions.openWeb("#/item/${it.id}") },
                modifier = Modifier.padding(start = px(200)),
            )
            val row = state.row
            if (row != null && !offline) {
                ContinueRow(
                    row = row,
                    imageUrl = imageUrl,
                    loader = loader,
                    onFocused = vm::onCardFocused,
                    onOpen = { actions.openWeb("#/play/${it.id}") },
                    modifier = Modifier.padding(top = px(26)),
                )
            }
        }
        Dock(
            apps = state.dock,
            loader = loader,
            firstFocus = dockFocus,
            onApp = actions.openApp,
            onLiveTv = actions.openLiveTv,
            onInputs = { inputs = actions.listInputs() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = px(36)),
        )
        // Drawn after the hero text so the expanded rail sits on top of it.
        Rail(
            listOf(
                RailEntry(LineIcon.Home, "Home") { runCatching { (if (shown != null) playFocus else dockFocus).requestFocus() } },
                RailEntry(LineIcon.Apps, "Apps", onClick = actions.openApps),
                RailEntry(LineIcon.Library, "Library", enabled = !offline) {
                    actions.openWeb(state.libraryId?.let { "#/library/$it" } ?: "#/")
                },
                RailEntry(LineIcon.Live, "Live", onClick = actions.openLiveTv),
                RailEntry(LineIcon.Inputs, "Inputs") { inputs = actions.listInputs() },
                RailEntry(LineIcon.Search, "Search", enabled = !offline) { actions.openWeb("#/search") },
            ),
            Modifier.padding(start = px(48), top = px(200)),
        )
        inputs?.let { list ->
            InputsDialog(list, onPick = { inputs = null; actions.openInput(it) }, onDismiss = { inputs = null })
        }
    }
}
