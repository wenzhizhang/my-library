package top.dingfengbo.mylibrary

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.data.auth.Session
import top.dingfengbo.mylibrary.data.auth.SessionState
import top.dingfengbo.mylibrary.ui.auth.AuthScreen
import top.dingfengbo.mylibrary.ui.books.BookDetailScreen
import top.dingfengbo.mylibrary.ui.books.BookFormScreen
import top.dingfengbo.mylibrary.ui.books.BookListScreen
import top.dingfengbo.mylibrary.ui.books.IsbnScanScreen
import top.dingfengbo.mylibrary.ui.catalog.CatalogDetailScreen
import top.dingfengbo.mylibrary.ui.catalog.CatalogListScreen
import top.dingfengbo.mylibrary.ui.collections.CollectionDetailScreen
import top.dingfengbo.mylibrary.ui.collections.CollectionListScreen
import top.dingfengbo.mylibrary.ui.plans.PlanDetailScreen
import top.dingfengbo.mylibrary.ui.export.ExportScreen
import top.dingfengbo.mylibrary.ui.plans.PlanListScreen
import top.dingfengbo.mylibrary.ui.stats.StatsScreen
import top.dingfengbo.mylibrary.ui.settings.SettingsScreen

/**
 * Top-level gate: the whole app follows [SessionState].
 *
 * Signed out — including "the token expired while you were using it" — means the login screen,
 * because this backend answers reads with the shared demo.db when no valid token is sent.
 */
@Composable
fun AppNavigation(container: AppContainer) {
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // The entries' ViewModelStores live in a ViewModel, so a configuration change keeps the screens'
    // state. They must not outlive the session: this empties them as soon as the signed-in branch is
    // left, which is how every ending arrives — sign-out, token expiry, a rejected token.
    val entryStores = viewModel { NavEntryStores() }
    val signedIn = sessionState is SessionState.LoggedIn
    DisposableEffect(signedIn) {
        if (!signedIn) entryStores.viewModelStore.clear()
        onDispose { }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch {
            container.sessionManager.restore()
            container.authRepository.verifySession()
        }
    }

    // A transparent background (the active picture) matches no ColorScheme slot, so Material3 cannot
    // pick the matching on-colour and page-level text falls back to LocalContentColor's black default
    // — unreadable over a dark blurred photo. Name it here: Surface publishes it to everything that
    // does not set its own colour; cards, bars and text fields already do.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        when (val state = sessionState) {
            SessionState.Unknown ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is SessionState.LoggedOut ->
                AuthScreen(repository = container.authRepository, expired = state.expired)

            is SessionState.LoggedIn -> MainNavigation(container, state.session, entryStores)
        }
    }
}

@Composable
private fun MainNavigation(container: AppContainer, session: Session, entryStores: NavEntryStores) {
    val backStack = rememberNavBackStack(BookList)
    val current = backStack.lastOrNull()

    Scaffold(
        // The bar is for the three places you can live in. Everything else is a task pushed on top
        // of one of them, and those keep their own Back instead of offering a lateral escape.
        bottomBar = {
            if (BottomDestinations.any { it.key == current }) {
                LibraryBottomBar(
                    current = current,
                    onSelect = { key ->
                        if (key != current) {
                            // The bar's entries are roots: drop what the library pushed before
                            // pushing the choice, so hopping between tabs cannot grow a stack.
                            while (backStack.size > 1) backStack.removeLastOrNull()
                            if (key != BookList) backStack.add(key)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            // Navigation3 renders entries with no motion at all, which reads as a cut between
            // screens. These specs are the whole motion budget: a short fade with a slight drift.
            transitionSpec = { forwardMotion() },
            popTransitionSpec = { backMotion() },
            // Without these decorators every entry shares the activity's ViewModelStore, so a screen's
            // viewModel { } would hand back the instance created for a *different* entry — opening
            // "add book" after viewing a book reused the edit screen's state (wrong title, prefilled
            // fields, and save would have overwritten the book that was open before).
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    // Fills `entryStores`, which the session's end empties — see NavEntryStores.
                    rememberViewModelStoreNavEntryDecorator(entryStores),
                ),
            modifier = Modifier.padding(innerPadding),
            entryProvider =
                entryProvider {
                    entry<BookList> {
                        BookListScreen(
                            container = container,
                            onOpenBook = { bookId -> backStack.add(BookDetail(bookId)) },
                            onCreateBook = { backStack.add(BookForm()) },
                            onOpenCatalog = { entity -> backStack.add(CatalogList(entity)) },
                            onOpenCollections = { backStack.add(CollectionList) },
                            onOpenPlans = { backStack.add(PlanList) },
                            onOpenScanner = { backStack.add(IsbnScan) },
                        )
                    }
                    entry<BookDetail> { key ->
                        BookDetailScreen(
                            container = container,
                            bookId = key.bookId,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { bookId -> backStack.add(BookForm(bookId)) },
                            onOpenBook = { bookId -> backStack.add(BookDetail(bookId)) },
                        )
                    }
                    entry<BookForm> { key ->
                        BookFormScreen(
                            container = container,
                            bookId = key.bookId,
                            onOpenScanner = { backStack.add(IsbnScan) },
                            onFinished = { backStack.removeLastOrNull() },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<IsbnScan> {
                        IsbnScanScreen(
                            onScanned = { code ->
                                // The form stays alive and picks the code up from the hand-off.
                                container.scanHandoff.publish(code)
                                backStack.removeLastOrNull()
                            },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<CatalogList> { key ->
                        CatalogListScreen(
                            container = container,
                            entity = key.entity,
                            onOpenEntity = { entityId -> backStack.add(CatalogDetail(key.entity, entityId)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<CatalogDetail> { key ->
                        CatalogDetailScreen(
                            container = container,
                            entity = key.entity,
                            entityId = key.entityId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenBook = { bookId -> backStack.add(BookDetail(bookId)) },
                        )
                    }
                    entry<CollectionList> {
                        CollectionListScreen(
                            container = container,
                            onOpenCollection = { id -> backStack.add(CollectionDetail(id)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<CollectionDetail> { key ->
                        CollectionDetailScreen(
                            container = container,
                            collectionId = key.collectionId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenBook = { bookId -> backStack.add(BookDetail(bookId)) },
                        )
                    }
                    entry<PlanList> {
                        PlanListScreen(
                            container = container,
                            onOpenPlan = { id -> backStack.add(PlanDetail(id)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<PlanDetail> { key ->
                        PlanDetailScreen(
                            container = container,
                            planId = key.planId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenBook = { bookId -> backStack.add(BookDetail(bookId)) },
                        )
                    }
                    entry<Stats> {
                        StatsScreen(container = container, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Export> {
                        ExportScreen(container = container, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Settings> {
                        SettingsScreen(
                            container = container,
                            session = session,
                            serverUrl = BuildConfig.BASE_URL,
                            onBack = { backStack.removeLastOrNull() },
                            onSignOut = { container.sessionManager.signOut() },
                            onOpenStats = { backStack.add(Stats) },
                            onOpenExport = { backStack.add(Export) },
                        )
                    }
                },
        )
    }
}

/** One entry of the bottom bar: a place to live, its glyph, and the label it has always had. */
private data class BottomDestination(val key: NavKey, val icon: ImageVector, val labelRes: Int)

private val BottomDestinations =
    listOf(
        BottomDestination(BookList, Icons.AutoMirrored.Filled.List, R.string.books_title),
        // The curated icon set has no chart glyph, and the extended set is a 10 MB dependency for
        // one icon; Info is the nearest honest choice for a statistics screen.
        BottomDestination(Stats, Icons.Default.Info, R.string.stats_title),
        BottomDestination(Settings, Icons.Default.Settings, R.string.settings_title),
    )

@Composable
private fun LibraryBottomBar(current: NavKey?, onSelect: (NavKey) -> Unit) {
    NavigationBar {
        BottomDestinations.forEach { destination ->
            NavigationBarItem(
                selected = current == destination.key,
                onClick = { onSelect(destination.key) },
                // The label is the item's accessible name, so the icon must not repeat it.
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

// 220ms of tween, no spring: the motion has to explain the direction of travel, not perform.
private const val ScreenMotionMillis = 220

private val screenSlide = tween<IntOffset>(ScreenMotionMillis)

private val screenFade = tween<Float>(ScreenMotionMillis)

/**
 * Forward motion: the new screen drifts in from the right, the old one steps aside by the same
 * fraction of the width, so it reads as a nudge rather than a full push.
 */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.forwardMotion(): ContentTransform =
    if (targetState.key is IsbnScan) {
        // The scanner is a task you finish, not a place you go: it rises from the bottom edge
        // instead of pushing the form sideways.
        (slideInVertically(screenSlide) { it } + fadeIn(screenFade)) togetherWith fadeOut(screenFade)
    } else {
        (slideInHorizontally(screenSlide) { it / 10 } + fadeIn(screenFade)) togetherWith
            (slideOutHorizontally(screenSlide) { -it / 10 } + fadeOut(screenFade))
    }

/** Back: the same motion mirrored, so going back undoes what going forward did. */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.backMotion(): ContentTransform =
    if (initialState.key is IsbnScan) {
        fadeIn(screenFade) togetherWith
            (slideOutVertically(screenSlide) { it } + fadeOut(screenFade))
    } else {
        (slideInHorizontally(screenSlide) { -it / 10 } + fadeIn(screenFade)) togetherWith
            (slideOutHorizontally(screenSlide) { it / 10 } + fadeOut(screenFade))
    }

/**
 * The ViewModelStores [NavDisplay] fills — one per entry key — held where a session can end without
 * taking the activity down with it.
 *
 * Being a ViewModel is what keeps the screens' state across a configuration change; emptying the store
 * when the session ends is what keeps one account's screens out of the next one's.
 */
private class NavEntryStores : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    override fun onCleared() = viewModelStore.clear()
}
