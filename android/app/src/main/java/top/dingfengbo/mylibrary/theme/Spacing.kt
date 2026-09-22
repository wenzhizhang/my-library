package top.dingfengbo.mylibrary.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One spacing rhythm for the whole app.
 *
 * The screens had drifted to 2/4/6/8/10/12/14/16/18/20/22/24 dp, which is why nothing lined up from
 * one screen to the next. Everything now comes off this scale, so a screen reader of the code can
 * tell what is a gap and what is a section break.
 */
object Spacing {
  /** 4dp: between a label and its value, icon and text. */
  val xs = 4.dp

  /** 8dp: between related controls. */
  val sm = 8.dp

  /** 12dp: inside a card, between rows of a list. */
  val md = 12.dp

  /** 16dp: the default screen gutter. */
  val lg = 16.dp

  /** 20dp: between groups inside a card. */
  val xl = 20.dp

  /** 24dp: between sections. */
  val xxl = 24.dp

  /** 32dp: above a screen's first section, below its last. */
  val xxxl = 32.dp
}

/**
 * Corner radii, from one family: small on inner elements, larger on the container that holds them.
 * Buttons and text fields share `medium` so nothing reads as a different design language.
 */
internal val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )
