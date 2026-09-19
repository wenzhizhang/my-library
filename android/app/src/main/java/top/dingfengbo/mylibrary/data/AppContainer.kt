package top.dingfengbo.mylibrary.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import top.dingfengbo.mylibrary.BuildConfig
import top.dingfengbo.mylibrary.api.apis.AuthApi
import top.dingfengbo.mylibrary.api.apis.AuthorsApi
import top.dingfengbo.mylibrary.api.apis.BackgroundsApi
import top.dingfengbo.mylibrary.api.apis.BookCollectionsApi
import top.dingfengbo.mylibrary.api.apis.BooksApi
import top.dingfengbo.mylibrary.api.apis.BookshelvesApi
import top.dingfengbo.mylibrary.api.apis.BrandsApi
import top.dingfengbo.mylibrary.api.apis.CategoriesApi
import top.dingfengbo.mylibrary.api.apis.ConfigApi
import top.dingfengbo.mylibrary.api.apis.ISBNApi
import top.dingfengbo.mylibrary.api.apis.PublishersApi
import top.dingfengbo.mylibrary.api.apis.ReadingPlansApi
import top.dingfengbo.mylibrary.api.apis.SeriesApi
import top.dingfengbo.mylibrary.api.apis.StatsApi
import top.dingfengbo.mylibrary.api.infrastructure.ApiClient
import top.dingfengbo.mylibrary.api.infrastructure.Serializer
import top.dingfengbo.mylibrary.data.auth.SessionManager
import top.dingfengbo.mylibrary.data.auth.TokenStore
import top.dingfengbo.mylibrary.data.net.AuthInterceptor
import top.dingfengbo.mylibrary.data.net.SafeRedirectInterceptor
import top.dingfengbo.mylibrary.data.net.apiConverterFactories

/**
 * Hand-wired object graph for the whole app: one OkHttp stack, one generated ApiClient,
 * one SessionManager. Small enough that a DI framework would only add a build step.
 */
class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext

    init {
        // The shared Json's default body shape: drop nulls. Creation needs that — its models declare
        // non-optional fields with defaults, so an explicit null comes back as a 422 ("Input should
        // be a valid string") — while the update routers read `exclude_unset`, where an explicit null
        // clears the column instead. That override is per request, in apiConverterFactories.
        // Must be set before the generated Json instance is first touched, hence the position here.
        Serializer.kotlinxSerializationJsonConfiguration = { explicitNulls = false }
    }

    /** Outlives any screen: the network layer needs to expire a session mid-request. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionManager: SessionManager = SessionManager(TokenStore(applicationContext), applicationScope)

    val libraryEvents = LibraryEvents()

    val scanHandoff = ScanHandoff()

    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            // Redirects are followed by SafeRedirectInterceptor instead: the backend's 307 points at
            // plain http, which a release build refuses to follow.
            .followRedirects(false)
            .addInterceptor(AuthInterceptor(sessionManager::validToken))
            .addInterceptor(SafeRedirectInterceptor())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            // The Authorization header must never reach logcat.
                            redactHeader("Authorization")
                            level = HttpLoggingInterceptor.Level.BASIC
                        }
                    )
                }
            }
            .build()

    // Decimals ride on the shared Json staying lenient: BigDecimalAdapter decodes with
    // `decodeString()`, which only accepts a bare JSON number because of that, and it writes decimals
    // as JSON strings, which the server coerces in lax mode — neither side may be "tidied" alone.
    private val apiClient =
        ApiClient(
            baseUrl = BuildConfig.BASE_URL,
            okHttpClientBuilder = okHttpClient.newBuilder(),
            converterFactories = apiConverterFactories,
        )

    val authApi: AuthApi = apiClient.createService(AuthApi::class.java)

    val booksApi: BooksApi = apiClient.createService(BooksApi::class.java)

    val isbnApi: ISBNApi = apiClient.createService(ISBNApi::class.java)

    val authRepository = AuthRepository(authApi, sessionManager)

    val bookRepository = BookRepository(booksApi, isbnApi)

    val catalogRepository = CatalogRepository(
        authorsApi = apiClient.createService(AuthorsApi::class.java),
        publishersApi = apiClient.createService(PublishersApi::class.java),
        brandsApi = apiClient.createService(BrandsApi::class.java),
        seriesApi = apiClient.createService(SeriesApi::class.java),
        categoriesApi = apiClient.createService(CategoriesApi::class.java),
        bookshelvesApi = apiClient.createService(BookshelvesApi::class.java),
    )

    val collectionRepository = CollectionRepository(apiClient.createService(BookCollectionsApi::class.java))

    val readingPlanRepository = ReadingPlanRepository(apiClient.createService(ReadingPlansApi::class.java))

    val statsRepository = StatsRepository(apiClient.createService(StatsApi::class.java))

    val preferencesRepository = PreferencesRepository(
        backgroundsApi = apiClient.createService(BackgroundsApi::class.java),
        configApi = apiClient.createService(ConfigApi::class.java),
    )

    /** The picture behind every screen; reloaded when the session or the chosen background changes. */
    val backgroundState = BackgroundState(preferencesRepository, applicationScope, sessionManager.state)

    /** Export downloads stream through this same client, so auth and 401 handling match the rest. */
    val exportRepository = ExportRepository(okHttpClient)
}
