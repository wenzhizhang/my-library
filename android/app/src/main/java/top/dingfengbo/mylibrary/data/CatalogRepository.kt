package top.dingfengbo.mylibrary.data

import top.dingfengbo.mylibrary.api.apis.AuthorsApi
import top.dingfengbo.mylibrary.api.apis.BookshelvesApi
import top.dingfengbo.mylibrary.api.apis.BrandsApi
import top.dingfengbo.mylibrary.api.apis.CategoriesApi
import top.dingfengbo.mylibrary.api.apis.PublishersApi
import top.dingfengbo.mylibrary.api.apis.SeriesApi
import top.dingfengbo.mylibrary.api.models.AuthorCreation
import top.dingfengbo.mylibrary.api.models.AuthorUpdate
import top.dingfengbo.mylibrary.api.models.BookCard
import top.dingfengbo.mylibrary.api.models.BookSeriesCreation
import top.dingfengbo.mylibrary.api.models.BookSeriesUpdate
import top.dingfengbo.mylibrary.api.models.BookshelfCreation
import top.dingfengbo.mylibrary.api.models.BookshelfUpdate
import top.dingfengbo.mylibrary.api.models.BrandCreation
import top.dingfengbo.mylibrary.api.models.BrandUpdate
import top.dingfengbo.mylibrary.api.models.CategoryCreation
import top.dingfengbo.mylibrary.api.models.CategoryUpdate
import top.dingfengbo.mylibrary.api.models.PublisherCreation
import top.dingfengbo.mylibrary.api.models.PublisherUpdate
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.CatalogPage
import top.dingfengbo.mylibrary.data.model.EntityAttribute
import top.dingfengbo.mylibrary.data.model.EntityDetail
import top.dingfengbo.mylibrary.data.model.EntityEdit
import top.dingfengbo.mylibrary.data.model.NamedRef
import top.dingfengbo.mylibrary.data.net.ApiErrors

/**
 * The six reference catalogs behind the book form's dropdowns, plus inline creation of new entries.
 *
 * Every list endpoint takes the same page/limit/q shape, so one private helper covers them and the
 * UI only ever sees [NamedRef].
 */
class CatalogRepository(
    private val authorsApi: AuthorsApi,
    private val publishersApi: PublishersApi,
    private val brandsApi: BrandsApi,
    private val seriesApi: SeriesApi,
    private val categoriesApi: CategoriesApi,
    private val bookshelvesApi: BookshelvesApi,
) {
    suspend fun authors(query: String, page: Int = 1): Result<CatalogPage> = ApiErrors.call {
        val response = authorsApi.apiAuthorsGet(page = page, limit = PAGE_SIZE, q = query.orNull())
        CatalogPage(
            items = response.authors.orEmpty().map {
                NamedRef(
                    id = it.id,
                    label = listOfNotNull(it.nameCn?.takeIf { name -> name.isNotBlank() }, it.name)
                        .joinToString(" "),
                    detail = listOfNotNull(it.dynasty?.takeIf { d -> d.isNotBlank() }, it.nation)
                        .joinToString(" ")
                        .takeIf { text -> text.isNotBlank() },
                )
            },
            page = page,
            totalPages = response.totalPages ?: 1,
        )
    }

    suspend fun publishers(query: String, page: Int = 1): Result<CatalogPage> = ApiErrors.call {
        val response = publishersApi.apiPublishersGet(page = page, limit = PAGE_SIZE, q = query.orNull())
        CatalogPage(
            items = response.publishers.orEmpty().map { NamedRef(it.id, it.name) },
            page = page,
            totalPages = response.totalPages ?: 1,
        )
    }

    suspend fun brands(query: String, page: Int = 1): Result<CatalogPage> = ApiErrors.call {
        val response = brandsApi.apiBrandsGet(page = page, limit = PAGE_SIZE, q = query.orNull())
        CatalogPage(
            items = response.brands.orEmpty().map { NamedRef(it.id, it.name) },
            page = page,
            totalPages = response.totalPages ?: 1,
        )
    }

    suspend fun series(query: String, page: Int = 1): Result<CatalogPage> = ApiErrors.call {
        val response = seriesApi.apiSeriesGet(page = page, limit = PAGE_SIZE, q = query.orNull())
        CatalogPage(
            items = response.series.orEmpty().map { NamedRef(it.id, it.name) },
            page = page,
            totalPages = response.totalPages ?: 1,
        )
    }

    /** Categories are hierarchical; the path is what tells two same-named leaves apart. */
    suspend fun categories(query: String, page: Int = 1): Result<CatalogPage> = ApiErrors.call {
        val response = categoriesApi.apiCategoriesGet(page = page, limit = PAGE_SIZE, q = query.orNull())
        CatalogPage(
            items = response.categories.orEmpty().map {
                NamedRef(it.id, it.path?.takeIf { path -> path.isNotBlank() } ?: it.name)
            },
            page = page,
            totalPages = response.totalPages ?: 1,
        )
    }

    suspend fun bookshelves(query: String, page: Int = 1): Result<CatalogPage> = ApiErrors.call {
        val response = bookshelvesApi.apiBookshelvesGet(page = page, limit = PAGE_SIZE, q = query.orNull())
        CatalogPage(
            items = response.bookshelves.orEmpty().map { NamedRef(it.id, it.name) },
            page = page,
            totalPages = response.totalPages ?: 1,
        )
    }

    suspend fun createAuthor(name: String, nameCn: String): Result<NamedRef> = ApiErrors.call {
        val created = authorsApi.apiAuthorsPost(AuthorCreation(name = name, nameCn = nameCn))
        NamedRef(created.id, nameCn)
    }

    suspend fun createPublisher(name: String): Result<NamedRef> = ApiErrors.call {
        val created = publishersApi.apiPublishersPost(PublisherCreation(name = name))
        NamedRef(created.id, created.name)
    }

    suspend fun createBrand(name: String): Result<NamedRef> = ApiErrors.call {
        val created = brandsApi.apiBrandsPost(BrandCreation(name = name))
        NamedRef(created.id, created.name)
    }

    suspend fun createSeries(name: String): Result<NamedRef> = ApiErrors.call {
        val created = seriesApi.apiSeriesPost(BookSeriesCreation(name = name))
        NamedRef(created.id, created.name)
    }

    suspend fun createCategory(name: String): Result<NamedRef> = ApiErrors.call {
        val created = categoriesApi.apiCategoriesPost(CategoryCreation(name = name))
        NamedRef(created.id, created.path?.takeIf { it.isNotBlank() } ?: created.name)
    }

    suspend fun createBookshelf(name: String): Result<NamedRef> = ApiErrors.call {
        val created = bookshelvesApi.apiBookshelvesPost(BookshelfCreation(name = name))
        NamedRef(created.id, created.name)
    }

    private fun String.orNull(): String? = trim().takeIf { it.isNotEmpty() }

    // ---- Detail / edit / delete / related books for the management screens -------------------

    suspend fun rows(entity: CatalogEntity, query: String, page: Int = 1): Result<CatalogPage> =
        when (entity) {
            CatalogEntity.Author -> authors(query, page)
            CatalogEntity.Publisher -> publishers(query, page)
            CatalogEntity.Brand -> brands(query, page)
            CatalogEntity.Series -> series(query, page)
            CatalogEntity.Category -> categories(query, page)
            CatalogEntity.Bookshelf -> bookshelves(query, page)
        }

    /** One entry point for the management screens' "new" action. */
    suspend fun create(entity: CatalogEntity, edit: EntityEdit): Result<NamedRef> = when (entity) {
        CatalogEntity.Author -> createAuthor(edit.name.trim(), edit.nameCn.trim().ifBlank { edit.name.trim() })
        CatalogEntity.Publisher -> createPublisher(edit.name.trim())
        CatalogEntity.Brand -> createBrand(edit.name.trim())
        CatalogEntity.Series -> createSeries(edit.name.trim())
        CatalogEntity.Category -> createCategory(edit.name.trim())
        CatalogEntity.Bookshelf -> createBookshelf(edit.name.trim())
    }

    suspend fun detail(entity: CatalogEntity, id: Int): Result<EntityDetail> = ApiErrors.call {
        when (entity) {
            CatalogEntity.Author -> authorsApi.apiAuthorsAuthorIdGet(id).let { it ->
                EntityDetail(
                    id = it.id,
                    title = listOfNotNull(it.nameCn?.takeIf { name -> name.isNotBlank() }, it.name)
                        .joinToString(" "),
                    photo = it.photo,
                    attributes = mapOf(
                        EntityAttribute.Name to it.name,
                        EntityAttribute.NameCn to it.nameCn,
                        EntityAttribute.Nation to it.nation,
                        EntityAttribute.Dynasty to it.dynasty,
                        EntityAttribute.Intro to it.intro,
                        EntityAttribute.Weight to it.weight?.toString(),
                        EntityAttribute.BookCount to it.books?.size?.toString(),
                    ),
                    edit = EntityEdit(
                        name = it.name,
                        nameCn = it.nameCn.orEmpty(),
                        nation = it.nation.orEmpty(),
                        dynasty = it.dynasty.orEmpty(),
                        intro = it.intro.orEmpty(),
                        photo = it.photo.orEmpty(),
                    ),
                )
            }

            CatalogEntity.Publisher -> publishersApi.apiPublishersPublisherIdGet(id).let { it ->
                EntityDetail(
                    id = it.id,
                    title = it.name,
                    photo = it.logo,
                    attributes = mapOf(
                        EntityAttribute.Name to it.name,
                        EntityAttribute.Intro to it.intro,
                        EntityAttribute.Weight to it.weight?.toString(),
                        EntityAttribute.BookCount to it.books?.size?.toString(),
                    ),
                    edit = EntityEdit(
                        name = it.name,
                        intro = it.intro.orEmpty(),
                        logo = it.logo.orEmpty(),
                    ),
                )
            }

            CatalogEntity.Brand -> brandsApi.apiBrandsBrandIdGet(id).let { it ->
                EntityDetail(
                    id = it.id,
                    title = it.name,
                    photo = null,
                    attributes = mapOf(
                        EntityAttribute.Name to it.name,
                        EntityAttribute.Intro to it.intro,
                        EntityAttribute.Weight to it.weight?.toString(),
                        EntityAttribute.BookCount to it.books?.size?.toString(),
                    ),
                    edit = EntityEdit(name = it.name, intro = it.intro.orEmpty()),
                )
            }

            CatalogEntity.Series -> seriesApi.apiSeriesSeriesIdGet(id).let { it ->
                EntityDetail(
                    id = it.id,
                    title = it.name,
                    photo = null,
                    attributes = mapOf(
                        EntityAttribute.Name to it.name,
                        EntityAttribute.Intro to it.intro,
                        EntityAttribute.Weight to it.weight?.toString(),
                        EntityAttribute.BookCount to it.books?.size?.toString(),
                    ),
                    edit = EntityEdit(name = it.name, intro = it.intro.orEmpty()),
                )
            }

            CatalogEntity.Category -> categoriesApi.apiCategoriesCategoryIdGet(id).let { it ->
                EntityDetail(
                    id = it.id,
                    title = it.path?.takeIf { path -> path.isNotBlank() } ?: it.name,
                    photo = null,
                    attributes = mapOf(
                        EntityAttribute.Name to it.name,
                        EntityAttribute.Path to it.path,
                        EntityAttribute.Depth to it.depth?.toString(),
                        EntityAttribute.Intro to it.intro,
                        EntityAttribute.Weight to it.weight?.toString(),
                        EntityAttribute.BookCount to it.books?.size?.toString(),
                    ),
                    edit = EntityEdit(
                        name = it.name,
                        intro = it.intro.orEmpty(),
                        parent = it.parent?.toString().orEmpty(),
                    ),
                )
            }

            CatalogEntity.Bookshelf -> bookshelvesApi.apiBookshelvesBookshelfIdGet(id).let { it ->
                EntityDetail(
                    id = it.id,
                    title = it.name,
                    photo = null,
                    attributes = mapOf(
                        EntityAttribute.Name to it.name,
                        EntityAttribute.Intro to it.intro,
                        EntityAttribute.BookCount to (it.totalBooks ?: it.books?.size)?.toString(),
                    ),
                    edit = EntityEdit(name = it.name, intro = it.intro.orEmpty()),
                )
            }
        }
    }

    suspend fun update(entity: CatalogEntity, id: Int, edit: EntityEdit): Result<Unit> =
        ApiErrors.call {
            when (entity) {
                CatalogEntity.Author -> authorsApi.apiAuthorsAuthorIdPut(
                    id,
                    AuthorUpdate(
                        name = edit.name.trim(),
                        nameCn = edit.nameCn.orNull(),
                        nation = edit.nation.orNull(),
                        dynasty = edit.dynasty.orNull(),
                        intro = edit.intro.orNull(),
                        photo = edit.photo.orNull(),
                    ),
                )

                CatalogEntity.Publisher -> publishersApi.apiPublishersPublisherIdPut(
                    id,
                    PublisherUpdate(name = edit.name.trim(), intro = edit.intro.orNull(), logo = edit.logo.orNull()),
                )

                CatalogEntity.Brand -> brandsApi.apiBrandsBrandIdPut(
                    id,
                    BrandUpdate(name = edit.name.trim(), intro = edit.intro.orNull()),
                )

                CatalogEntity.Series -> seriesApi.apiSeriesSeriesIdPut(
                    id,
                    BookSeriesUpdate(name = edit.name.trim(), intro = edit.intro.orNull()),
                )

                CatalogEntity.Category -> categoriesApi.apiCategoriesCategoryIdPut(
                    id,
                    CategoryUpdate(
                        name = edit.name.trim(),
                        parent = edit.parent.trim().toIntOrNull(),
                        intro = edit.intro.orNull(),
                    ),
                )

                CatalogEntity.Bookshelf -> bookshelvesApi.apiBookshelvesBookshelfIdPut(
                    id,
                    BookshelfUpdate(name = edit.name.trim(), intro = edit.intro.orNull()),
                )
            }
            Unit
        }

    suspend fun delete(entity: CatalogEntity, id: Int): Result<Unit> = ApiErrors.call {
        when (entity) {
            CatalogEntity.Author -> authorsApi.apiAuthorsAuthorIdDelete(id)
            CatalogEntity.Publisher -> publishersApi.apiPublishersPublisherIdDelete(id)
            CatalogEntity.Brand -> brandsApi.apiBrandsBrandIdDelete(id)
            CatalogEntity.Series -> seriesApi.apiSeriesSeriesIdDelete(id)
            CatalogEntity.Category -> categoriesApi.apiCategoriesCategoryIdDelete(id)
            CatalogEntity.Bookshelf -> bookshelvesApi.apiBookshelvesBookshelfIdDelete(id)
        }
    }

    /** The books that reference this entity — the same slim list shape as the library. */
    suspend fun books(
        entity: CatalogEntity,
        id: Int,
        page: Int,
        limit: Int = ENTITY_BOOKS_LIMIT,
    ): Result<List<BookCard>> = ApiErrors.call {
        when (entity) {
            CatalogEntity.Author -> authorsApi.apiAuthorsAuthorIdBooksGet(id, page, limit).books
            CatalogEntity.Publisher -> publishersApi.apiPublishersPublisherIdBooksGet(id, page, limit).books
            CatalogEntity.Brand -> brandsApi.apiBrandsBrandIdBooksGet(id, page, limit).books
            CatalogEntity.Series -> seriesApi.apiSeriesSeriesIdBooksGet(id, page, limit).books
            CatalogEntity.Category -> categoriesApi.apiCategoriesCategoryIdBooksGet(id, page, limit).books
            CatalogEntity.Bookshelf -> bookshelvesApi.apiBookshelvesBookshelfIdBooksGet(id, page, limit).books
        }.orEmpty()
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val ENTITY_BOOKS_LIMIT = 50
    }
}
