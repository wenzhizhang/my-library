package top.dingfengbo.mylibrary.ui.common

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/**
 * Touch feedback for the moments where sight is not enough.
 *
 * A phone in one hand while the other holds the book: the scanner confirming a code, a long press
 * copying an ISBN, a destructive delete asking twice. Those are the places the app now answers with
 * a tick instead of relying on the reader noticing a colour change.
 */
@Composable
fun rememberHaptics(): Haptics {
  val view = LocalView.current
  return Haptics(
    confirm = { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) },
    reject = { view.performHapticFeedback(HapticFeedbackConstants.REJECT) },
    longPress = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
  )
}

class Haptics(
  val confirm: () -> Unit,
  val reject: () -> Unit,
  val longPress: () -> Unit,
)
