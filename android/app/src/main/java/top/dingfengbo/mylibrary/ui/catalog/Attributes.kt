package top.dingfengbo.mylibrary.ui.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.EntityAttribute

/** Display order and labels for an entity's attributes; anything missing is simply not shown. */
val attributeOrder = listOf(
    EntityAttribute.Name,
    EntityAttribute.NameCn,
    EntityAttribute.Nation,
    EntityAttribute.Dynasty,
    EntityAttribute.Path,
    EntityAttribute.Parent,
    EntityAttribute.Depth,
    EntityAttribute.Intro,
    EntityAttribute.Weight,
    EntityAttribute.Photo,
    EntityAttribute.Logo,
    EntityAttribute.BookCount,
)

fun EntityAttribute.labelRes(): Int = when (this) {
    EntityAttribute.Name -> R.string.attr_name
    EntityAttribute.NameCn -> R.string.attr_name_cn
    EntityAttribute.Nation -> R.string.attr_nation
    EntityAttribute.Dynasty -> R.string.attr_dynasty
    EntityAttribute.Intro -> R.string.attr_intro
    EntityAttribute.Photo -> R.string.attr_photo
    EntityAttribute.Logo -> R.string.attr_logo
    EntityAttribute.Weight -> R.string.attr_weight
    EntityAttribute.Parent -> R.string.attr_parent
    EntityAttribute.Path -> R.string.attr_path
    EntityAttribute.Depth -> R.string.attr_depth
    EntityAttribute.BookCount -> R.string.attr_book_count
}

/**
 * The glyph that marks an entry in the list.
 *
 * The six catalogs share one screen and one row shape, so without this an author and a bookshelf
 * are the same line of text. Each kind gets a distinct silhouette from the curated icon set.
 */
val CatalogEntity.markerIcon: ImageVector
    get() = when (this) {
        CatalogEntity.Author -> Icons.Default.Person
        CatalogEntity.Publisher -> Icons.Default.Build
        CatalogEntity.Brand -> Icons.Default.Star
        CatalogEntity.Series -> Icons.Default.Menu
        CatalogEntity.Category -> Icons.Default.AccountBox
        CatalogEntity.Bookshelf -> Icons.Default.Home
    }

/**
 * Attribute values that are numbers — an id, a weight, a count.
 *
 * They render with tabular figures: proportional digits make a column of ids or counts wobble.
 */
val EntityAttribute.numericValue: Boolean
    get() = when (this) {
        EntityAttribute.Parent,
        EntityAttribute.Depth,
        EntityAttribute.Weight,
        EntityAttribute.BookCount -> true

        else -> false
    }

/** Attribute values that are stored paths rather than prose, and read as such in monospace. */
val EntityAttribute.identifierValue: Boolean
    get() = this == EntityAttribute.Photo || this == EntityAttribute.Logo
