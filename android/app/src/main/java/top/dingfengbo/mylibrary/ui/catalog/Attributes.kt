package top.dingfengbo.mylibrary.ui.catalog

import top.dingfengbo.mylibrary.R
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
