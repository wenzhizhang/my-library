package top.dingfengbo.mylibrary

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import top.dingfengbo.mylibrary.data.model.CatalogEntity

@Serializable data object BookList : NavKey

@Serializable data class BookDetail(val bookId: Int) : NavKey

/** [bookId] null means "create"; the form's VM loads the book when it is set. */
@Serializable data class BookForm(val bookId: Int? = null) : NavKey

@Serializable data object IsbnScan : NavKey

@Serializable data class CatalogList(val entity: CatalogEntity) : NavKey

@Serializable data class CatalogDetail(val entity: CatalogEntity, val entityId: Int) : NavKey

@Serializable data object CollectionList : NavKey

@Serializable data class CollectionDetail(val collectionId: Int) : NavKey

@Serializable data object PlanList : NavKey

@Serializable data class PlanDetail(val planId: Int) : NavKey

@Serializable data object Stats : NavKey

@Serializable data object Export : NavKey

@Serializable data object Settings : NavKey
