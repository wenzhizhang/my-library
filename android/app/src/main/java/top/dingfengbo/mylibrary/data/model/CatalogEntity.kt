package top.dingfengbo.mylibrary.data.model

import top.dingfengbo.mylibrary.R

/**
 * The six reference catalogs that have their own management screens.
 *
 * [editable] is exactly the field set the matching `*Creation` and `*Update` schemas accept, so the
 * same list drives both the create and the edit form.
 */
enum class CatalogEntity(val titleRes: Int, val editable: List<EntityAttribute>) {
    Author(
        R.string.catalog_authors,
        listOf(
            EntityAttribute.Name,
            EntityAttribute.NameCn,
            EntityAttribute.Nation,
            EntityAttribute.Dynasty,
            EntityAttribute.Intro,
            EntityAttribute.Photo,
        ),
    ),
    Publisher(
        R.string.catalog_publishers,
        listOf(EntityAttribute.Name, EntityAttribute.Intro, EntityAttribute.Logo),
    ),
    Brand(R.string.catalog_brands, listOf(EntityAttribute.Name, EntityAttribute.Intro)),
    Series(R.string.catalog_series, listOf(EntityAttribute.Name, EntityAttribute.Intro)),
    Category(
        R.string.catalog_categories,
        listOf(EntityAttribute.Name, EntityAttribute.Intro, EntityAttribute.Parent),
    ),
    Bookshelf(R.string.catalog_bookshelves, listOf(EntityAttribute.Name, EntityAttribute.Intro));
}

/** Everything any of the six entities can show or edit. */
enum class EntityAttribute {
    Name,
    NameCn,
    Nation,
    Dynasty,
    Intro,
    Photo,
    Logo,
    Weight,
    Parent,
    Path,
    Depth,
    BookCount,
}

/** What the edit form sends. Entities ignore the fields they do not have. */
data class EntityEdit(
    val name: String = "",
    val nameCn: String = "",
    val nation: String = "",
    val dynasty: String = "",
    val intro: String = "",
    val photo: String = "",
    val logo: String = "",
    val parent: String = "",
)

data class EntityDetail(
    val id: Int,
    val title: String,
    val photo: String?,
    val attributes: Map<EntityAttribute, String?>,
    val edit: EntityEdit,
)
