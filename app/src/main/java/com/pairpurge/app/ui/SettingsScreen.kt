package com.pairpurge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pairpurge.app.R
import com.pairpurge.app.settings.DeletionPolicy
import com.pairpurge.app.ui.theme.PairPurgeTheme

@Composable
fun SettingsScreen(policy: DeletionPolicy, onSave: (DeletionPolicy) -> Unit, modifier: Modifier = Modifier) {
    var enabled by rememberSaveable(policy.days) { mutableStateOf(policy.days != null) }
    var days by rememberSaveable(policy.days) { mutableStateOf((policy.days ?: 30).toString()) }
    val parsedDays = days.toIntOrNull()?.takeIf { it in 1..3650 }
    val valid = !enabled || parsedDays != null
    val changed = enabled != (policy.days != null) || (enabled && parsedDays != policy.days)
    val colors = PairPurgeTheme.colors
    val toggleLabel = stringResource(R.string.auto_delete_title)
    val uriHandler = LocalUriHandler.current

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.auto_delete_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
            )
            Switch(checked = enabled, onCheckedChange = { enabled = it }, modifier = Modifier.semantics { contentDescription = toggleLabel })
        }
        Text(stringResource(R.string.auto_delete_description), style = MaterialTheme.typography.bodyLarge, color = colors.muted)
        OutlinedTextField(
            value = days,
            onValueChange = { days = it },
            label = { Text(stringResource(R.string.auto_delete_days)) },
            supportingText = { Text(stringResource(R.string.auto_delete_range)) },
            enabled = enabled,
            isError = enabled && parsedDays == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.auto_delete_timing), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        Button(
            onClick = { if (valid) onSave(DeletionPolicy(if (enabled) parsedDays else null)) },
            enabled = valid && changed,
        ) {
            Text(stringResource(R.string.save_settings))
        }
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
        // Play requires the privacy policy to be reachable from inside the app, not only
        // from the store listing. Ink rather than terracotta: this is not a destructive action.
        TextButton(
            onClick = {
                try {
                    uriHandler.openUri(PRIVACY_POLICY_URL)
                } catch (_: IllegalArgumentException) {
                    // No app on the phone can open web links.
                }
            },
            colors = ButtonDefaults.textButtonColors(contentColor = colors.ink),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                stringResource(R.string.privacy_policy),
                style = MaterialTheme.typography.labelLarge,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

private const val PRIVACY_POLICY_URL = "https://github.com/farazz0/pairpurge/blob/main/PRIVACY.md"
