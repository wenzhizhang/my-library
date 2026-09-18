package top.dingfengbo.mylibrary.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.net.ApiException
import top.dingfengbo.mylibrary.data.net.ErrorKind

/**
 * Turns a failed call into something worth showing.
 *
 * Validation failures (400/422) surface the backend's own `detail`, because it says more than
 * "出错了" — for instance that the username is taken.
 */
@Composable
fun errorMessage(throwable: Throwable?): String? {
    val api = throwable as? ApiException ?: return throwable?.let { stringResource(R.string.error_unknown) }
    return when (api.kind) {
        ErrorKind.Network -> stringResource(R.string.error_network)
        ErrorKind.Unauthorized -> stringResource(R.string.error_unauthorized)
        ErrorKind.Expired -> stringResource(R.string.error_expired)
        ErrorKind.NotFound -> stringResource(R.string.error_not_found)
        ErrorKind.Server -> stringResource(R.string.error_server)
        ErrorKind.Validation -> api.detail ?: stringResource(R.string.error_unknown)
        ErrorKind.Unknown -> api.detail ?: stringResource(R.string.error_unknown)
    }
}
