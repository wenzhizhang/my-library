package top.dingfengbo.mylibrary.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AuthRepository
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

    Column(
        modifier = modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = ui.mode == AuthMode.Login,
                onClick = { onModeChange(AuthMode.Login) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.auth_login_tab))
            }
            SegmentedButton(
                selected = ui.mode == AuthMode.Register,
                onClick = { onModeChange(AuthMode.Register) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.auth_register_tab))
            }
        }

        Spacer(Modifier.height(16.dp))

        if (expired) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Text(
                    text = stringResource(R.string.auth_expired),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = ui.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.auth_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = ui.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (ui.mode == AuthMode.Login) ImeAction.Done else ImeAction.Next,
            ),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        stringResource(
                            if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (ui.mode == AuthMode.Register) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(stringResource(R.string.auth_confirm_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val message = if (ui.passwordMismatch) {
            stringResource(R.string.auth_password_mismatch)
        } else {
            errorMessage(ui.error)
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(onClick = onSubmit, enabled = ui.canSubmit, modifier = Modifier.fillMaxWidth()) {
            if (ui.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    stringResource(
                        if (ui.mode == AuthMode.Login) R.string.auth_login_action else R.string.auth_register_action
                    )
                )
            }
        }
    }
}
