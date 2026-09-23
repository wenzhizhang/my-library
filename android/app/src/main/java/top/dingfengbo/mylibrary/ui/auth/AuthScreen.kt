package top.dingfengbo.mylibrary.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AuthRepository
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.common.errorMessage

@Composable
fun AuthScreen(
    repository: AuthRepository,
    expired: Boolean,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel { AuthViewModel(repository) },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    AuthContent(
        ui = ui,
        expired = expired,
        onModeChange = viewModel::onModeChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
internal fun AuthContent(
    ui: AuthUiState,
    expired: Boolean,
    onModeChange: (AuthMode) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // The field is an error state rather than a neutral one when the two passwords disagree: a red
    // outline under the field the message is about is what tells the reader where to look.
    val mismatchSupport: (@Composable () -> Unit)? =
        if (ui.passwordMismatch) {
            { Text(stringResource(R.string.auth_password_mismatch)) }
        } else {
            null
        }

    Column(
        modifier =
            modifier.fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xxl, vertical = Spacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // This screen is the first thing the app says, so it introduces the app before asking for
        // anything: the name at display weight, then one line about what is behind it.
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.xxxl))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = ui.mode == AuthMode.Login,
                onClick = { onModeChange(AuthMode.Login) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.auth_login_tab))
            }
            SegmentedButton(
                selected = ui.mode == AuthMode.Register,
                onClick = { onModeChange(AuthMode.Register) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.auth_register_tab))
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        if (expired) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = stringResource(R.string.auth_expired),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        OutlinedTextField(
            value = ui.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.auth_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = ui.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation =
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (ui.mode == AuthMode.Login) ImeAction.Done else ImeAction.Next,
                ),
            trailingIcon = {
                TextButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (passwordVisible) R.string.auth_hide_password
                            else R.string.auth_show_password
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Second password only exists while registering, so it grows in and out rather than making
        // the button jump under the reader's thumb.
        AnimatedVisibility(
            visible = ui.mode == AuthMode.Register,
            enter = fadeIn(tween(200)) + expandVertically(tween(250)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = ui.confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = { Text(stringResource(R.string.auth_confirm_password)) },
                    singleLine = true,
                    isError = ui.passwordMismatch,
                    supportingText = mismatchSupport,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // What is left once the fields have had their say: connectivity, the server, a rejected pair.
        // It belongs to the form, not to one input, so it sits with the button that produced it.
        errorMessage(ui.error)?.let { message ->
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        Button(
            onClick = onSubmit,
            enabled = ui.canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (ui.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    stringResource(
                        if (ui.mode == AuthMode.Login) R.string.auth_login_action
                        else R.string.auth_register_action
                    )
                )
            }
        }
    }
}
