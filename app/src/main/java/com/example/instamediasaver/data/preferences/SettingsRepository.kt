package com.example.instamediasaver.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.instamediasaver.model.ResolverMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val darkMode: Boolean = true,
    val languageTag: String = "en",
    val resolverMode: ResolverMode = ResolverMode.BACKEND_PROXY,
    val backendBaseUrl: String = "https://your-backend-proxy.example.com",
    val backendBearerToken: String = "",
    val graphApiAccessToken: String = "",
    val graphApiUserId: String = ""
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "insta_saver_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("insta_saver_fallback_prefs", Context.MODE_PRIVATE)
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val modeName = prefs.getString("resolver_mode", ResolverMode.BACKEND_PROXY.name)
            ?: ResolverMode.BACKEND_PROXY.name
        val mode = runCatching { ResolverMode.valueOf(modeName) }
            .getOrDefault(ResolverMode.BACKEND_PROXY)

        return AppSettings(
            darkMode = prefs.getBoolean("dark_mode", true),
            languageTag = prefs.getString("language_tag", "en") ?: "en",
            resolverMode = mode,
            backendBaseUrl = prefs.getString("backend_base_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000",
            backendBearerToken = prefs.getString("backend_bearer_token", "") ?: "",
            graphApiAccessToken = prefs.getString("graph_api_token", "") ?: "",
            graphApiUserId = prefs.getString("graph_api_user_id", "") ?: ""
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putBoolean("dark_mode", newSettings.darkMode)
            .putString("language_tag", newSettings.languageTag)
            .putString("resolver_mode", newSettings.resolverMode.name)
            .putString("backend_base_url", newSettings.backendBaseUrl.trim().trimEnd('/'))
            .putString("backend_bearer_token", newSettings.backendBearerToken.trim())
            .putString("graph_api_token", newSettings.graphApiAccessToken.trim())
            .putString("graph_api_user_id", newSettings.graphApiUserId.trim())
            .apply()
        _settings.value = loadSettings()
    }
}
