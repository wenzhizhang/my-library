package top.dingfengbo.mylibrary.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

/**
 * Touch feedback for the moments where sight is not enough.
 *
 * A phone in one hand while the other holds the book: the scanner confirming a code, a copy landing,
 * a delete asking twice. Those are the places the app answers with a tick instead of relying on the
 * reader noticing a colour change.
 *
 * The compat constants matter on this app's floor: CONFIRM and REJECT only exist from API 30, and the
 * framework ignores an effect it does not know, so asking for them directly would leave Android 8-10
 * readers with a scanner that never ticks. HapticFeedbackConstantsCompat maps each one down.
 */
@Composable
fun rememberHaptics(): Haptics {
  val view = LocalView.current
  return Haptics(
    confirm = { ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM) },
    reject = { ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.REJECT) },
  )
}

class Haptics(
  val confirm: () -> Unit,
  val reject: () -> Unit,
)
