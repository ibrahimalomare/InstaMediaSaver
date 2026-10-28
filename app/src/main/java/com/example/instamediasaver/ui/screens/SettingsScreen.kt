package com.example.instamediasaver.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.instamediasaver.R
import com.example.instamediasaver.data.preferences.AppSettings
import com.example.instamediasaver.model.ResolverMode

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    currentSettings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit
) {
    var darkMode by remember(currentSettings) { mutableStateOf(currentSettings.darkMode) }
    var languageTag by remember(currentSettings) { mutableStateOf(currentSettings.languageTag) }
    var resolverMode by remember(currentSettings) { mutableStateOf(currentSettings.resolverMode) }
    var backendUrl by remember(currentSettings) { mutableStateOf(currentSettings.backendBaseUrl) }
    var backendToken by remember(currentSettings) { mutableStateOf(currentSettings.backendBearerToken) }
    var graphToken by remember(currentSettings) { mutableStateOf(currentSettings.graphApiAccessToken) }
    var graphUserId by remember(currentSettings) { mutableStateOf(currentSettings.graphApiUserId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Appearance & Bilingual Language Switcher (English / Arabic)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_dark_mode))
                    Switch(checked = darkMode, onCheckedChange = { darkMode = it })
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { languageTag = "en" },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.lang_english),
                            fontWeight = if (languageTag == "en") FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    OutlinedButton(
                        onClick = { languageTag = "ar" },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.lang_arabic),
                            fontWeight = if (languageTag == "ar") FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Resolver Mode Selection
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_resolver_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                ResolverModeOption(
                    selected = resolverMode == ResolverMode.BACKEND_PROXY,
                    label = stringResource(R.string.mode_backend_proxy),
                    onClick = { resolverMode = ResolverMode.BACKEND_PROXY }
                )
                ResolverModeOption(
                    selected = resolverMode == ResolverMode.DIRECT_GRAPH_API,
                    label = stringResource(R.string.mode_direct_graph),
                    onClick = { resolverMode = ResolverMode.DIRECT_GRAPH_API }
                )
                ResolverModeOption(
                    selected = resolverMode == ResolverMode.PUBLIC_OEMBED_INSPECTOR,
                    label = stringResource(R.string.mode_public_oembed),
                    onClick = { resolverMode = ResolverMode.PUBLIC_OEMBED_INSPECTOR }
                )

                Spacer(Modifier.height(12.dp))

                if (resolverMode == ResolverMode.BACKEND_PROXY) {
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text(stringResource(R.string.settings_backend_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendToken,
                        onValueChange = { backendToken = it },
                        label = { Text(stringResource(R.string.settings_backend_token)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else if (resolverMode == ResolverMode.DIRECT_GRAPH_API) {
                    OutlinedTextField(
                        value = graphUserId,
                        onValueChange = { graphUserId = it },
                        label = { Text(stringResource(R.string.settings_graph_user_id)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = graphToken,
                        onValueChange = { graphToken = it },
                        label = { Text(stringResource(R.string.settings_graph_token)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                onSaveSettings(
                    AppSettings(
                        darkMode = darkMode,
                        languageTag = languageTag,
                        resolverMode = resolverMode,
                        backendBaseUrl = backendUrl,
                        backendBearerToken = backendToken,
                        graphApiAccessToken = graphToken,
                        graphApiUserId = graphUserId
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.btn_save_settings), fontWeight = FontWeight.Bold)
        }

        // Privacy & Compliance Notice
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.privacy_notice_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.privacy_notice_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResolverModeOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
