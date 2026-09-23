package top.dingfengbo.mylibrary

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.common.appBarFill
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
    var quickActions by remember { mutableStateOf(false) }

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
                    onScan = { quickActions = true },
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

        // The bar's middle button opens this. It is a task, not a destination, so it lives beside the
        // bar rather than in the navigation graph.
        if (quickActions) {
            QuickActionsSheet(
                onDismiss = { quickActions = false },
                onSearch = {
                    quickActions = false
                    // Search lives in the book list, which is not even composed while the reader is on
                    // another tab: go there first, then ask it to open the field.
                    while (backStack.size > 1) backStack.removeLastOrNull()
                    container.libraryEvents.requestSearch()
                },
                onAddBook = { quickActions = false; backStack.add(BookForm()) },
            )
        }
    }
}

/**
 * What the bar's middle button opens: the three things the library is opened to do. It lives here
 * because the bar does, and because two of the three are pushes this file already owns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsSheet(
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onAddBook: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.xxl)) {
            // One way in, two ways to fill it: the form's ISBN row carries the scanner, so scanning is
            // a step inside adding a book rather than a second entry beside it.
            QuickActionRow(
                icon = { Icon(painterResource(R.drawable.ic_qr_scan), contentDescription = null) },
                label = stringResource(R.string.books_add_new),
                onClick = onAddBook,
            )
            QuickActionRow(
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = stringResource(R.string.common_search),
                onClick = onSearch,
            )
        }
    }
}

/** One row of that sheet: an icon, a name, and the whole row as the target. */
@Composable
private fun QuickActionRow(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(Spacing.md))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One entry of the bottom bar: a place to live, its glyph, and the label it has always had. */
private data class BottomDestination(val key: NavKey, val icon: ImageVector, val labelRes: Int)

private val BottomDestinations =
    listOf(
        BottomDestination(BookList, Icons.AutoMirrored.Filled.List, R.string.books_title),
        // Settings holds the account, statistics, export and sign-out, so the bar calls it Mine.
        BottomDestination(Settings, Icons.Default.Person, R.string.mine_title),
    )

@Composable
private fun LibraryBottomBar(current: NavKey?, onSelect: (NavKey) -> Unit, onScan: () -> Unit) {
    val (books, mine) = BottomDestinations
    NavigationBar(containerColor = appBarFill()) {
        // Books, then the action, then Mine: the middle of the bar is what the reader came to do, and
        // it is never highlighted because it is a task rather than a place to live.
        // The label is each item's accessible name, so the icons must not repeat it.
        NavigationBarItem(
            selected = current == books.key,
            onClick = { onSelect(books.key) },
            icon = { Icon(books.icon, contentDescription = null) },
            label = { Text(stringResource(books.labelRes)) },
        )
        NavigationBarItem(
            selected = false,
            onClick = onScan,
            icon = { Icon(painterResource(R.drawable.ic_qr_scan), contentDescription = null) },
            label = { Text(stringResource(R.string.books_quick_action)) },
        )
        NavigationBarItem(
            selected = current == mine.key,
            onClick = { onSelect(mine.key) },
            icon = { Icon(mine.icon, contentDescription = null) },
            label = { Text(stringResource(mine.labelRes)) },
        )
    }
}

// 380ms of decelerating travel: the new screen crosses the whole width so the reader can see where
// it came from, and the old one steps aside by a quarter instead of being yanked off the edge. The
// fade is shorter than the slide on purpose - a fade as slow as the travel leaves both screens
// ghosting over each other, which is the opposite of smooth.
private const val ScreenMotionMillis = 380
private const val FadeMotionMillis = 220

private val screenSlide = tween<IntOffset>(ScreenMotionMillis, easing = FastOutSlowInEasing)

private val screenStepAside = tween<IntOffset>(ScreenMotionMillis, easing = FastOutSlowInEasing)

private val screenFade = tween<Float>(FadeMotionMillis)

/**
 * Forward motion: the new screen slides in from the right edge across the whole width, and the old
 * one steps a quarter of the way left behind it. That quarter is what makes the pair read as one
 * gesture with a direction rather than as two screens swapping.
 */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.forwardMotion(): ContentTransform =
    if (targetState.key is IsbnScan) {
        // The scanner is a task you finish, not a place you go: it rises from the bottom edge
        // instead of pushing the form sideways.
        (slideInVertically(screenSlide) { it } + fadeIn(screenFade)) togetherWith fadeOut(screenFade)
    } else {
        (slideInHorizontally(screenSlide) { it } + fadeIn(screenFade)) togetherWith
            (slideOutHorizontally(screenStepAside) { -it / 4 } + fadeOut(screenFade))
    }

/** Back: the same motion mirrored, so going back undoes what going forward did. */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.backMotion(): ContentTransform =
    if (initialState.key is IsbnScan) {
        fadeIn(screenFade) togetherWith
            (slideOutVertically(screenSlide) { it } + fadeOut(screenFade))
    } else {
        (slideInHorizontally(screenStepAside) { -it / 4 } + fadeIn(screenFade)) togetherWith
            (slideOutHorizontally(screenSlide) { it } + fadeOut(screenFade))
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
