package top.dingfengbo.mylibrary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
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

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Without these decorators every entry shares the activity's ViewModelStore, so a screen's
        // viewModel { } would hand back the instance created for a *different* entry — opening
        // "add book" after viewing a book reused the edit screen's state (wrong title, prefilled
        // fields, and save would have overwritten the book that was open before).
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // Fills `entryStores`, which the session's end empties — see NavEntryStores.
            rememberViewModelStoreNavEntryDecorator(entryStores),
        ),
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
                        onOpenSettings = { backStack.add(Settings) },
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
