package com.argon.stream.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.argon.stream.domain.model.ArgonPreferences
import com.argon.stream.domain.model.ExternalPlayerTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.argonDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "argon_user_preferences"
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val DEFAULT_PLAYER = stringPreferencesKey("default_player")
        val AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val SUBS_DISABLED = booleanPreferencesKey("subtitles_disabled_default")
        val AUTO_NEXT_EPISODE = booleanPreferencesKey("auto_play_next_episode")
        val MAX_RESOLUTION = stringPreferencesKey("preferred_max_resolution")
        val PREFER_CACHED = booleanPreferencesKey("prefer_cached_debrid")
        val EXCLUDED_CODECS = stringPreferencesKey("excluded_codecs_csv")
        val NETWORK_TIMEOUT = intPreferencesKey("network_timeout_seconds")
        val IMAGE_CACHE_MB = intPreferencesKey("image_cache_limit_mb")
    }

    val preferencesFlow: Flow<ArgonPreferences> = context.argonDataStore.data.map { prefs ->
        val playerName = prefs[Keys.DEFAULT_PLAYER] ?: ExternalPlayerTarget.INTERNAL_EXOPLAYER.name
        val resolvedPlayer = runCatching { ExternalPlayerTarget.valueOf(playerName) }
            .getOrDefault(ExternalPlayerTarget.INTERNAL_EXOPLAYER)

        ArgonPreferences(
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            dynamicColors = prefs[Keys.DYNAMIC_COLORS] ?: false,
            defaultPlayer = resolvedPlayer,
            preferredAudioLanguage = prefs[Keys.AUDIO_LANG] ?: "en",
            preferredSubtitleLanguage = prefs[Keys.SUBTITLE_LANG] ?: "en",
            subtitlesDisabledByDefault = prefs[Keys.SUBS_DISABLED] ?: false,
            autoPlayNextEpisode = prefs[Keys.AUTO_NEXT_EPISODE] ?: true,
            preferredMaxResolution = prefs[Keys.MAX_RESOLUTION] ?: "2160p",
            preferCachedDebridStreams = prefs[Keys.PREFER_CACHED] ?: true,
            excludedCodecsCsv = prefs[Keys.EXCLUDED_CODECS] ?: "",
            networkTimeoutSeconds = prefs[Keys.NETWORK_TIMEOUT] ?: 20,
            imageCacheLimitMb = prefs[Keys.IMAGE_CACHE_MB] ?: 250
        )
    }

    suspend fun updateTheme(darkTheme: Boolean, dynamicColors: Boolean) {
        context.argonDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = darkTheme
            prefs[Keys.DYNAMIC_COLORS] = dynamicColors
        }
    }

    suspend fun updateDefaultPlayer(target: ExternalPlayerTarget) {
        context.argonDataStore.edit { it[Keys.DEFAULT_PLAYER] = target.name }
    }

    suspend fun updateLanguages(audioLang: String, subtitleLang: String, disableSubsDefault: Boolean) {
        context.argonDataStore.edit { prefs ->
            prefs[Keys.AUDIO_LANG] = audioLang.lowercase().trim()
            prefs[Keys.SUBTITLE_LANG] = subtitleLang.lowercase().trim()
            prefs[Keys.SUBS_DISABLED] = disableSubsDefault
        }
    }

    suspend fun updateStreamFilters(
        maxResolution: String,
        preferCached: Boolean,
        excludedCodecsCsv: String,
        autoPlayNext: Boolean
    ) {
        context.argonDataStore.edit { prefs ->
            prefs[Keys.MAX_RESOLUTION] = maxResolution
            prefs[Keys.PREFER_CACHED] = preferCached
            prefs[Keys.EXCLUDED_CODECS] = excludedCodecsCsv
            prefs[Keys.AUTO_NEXT_EPISODE] = autoPlayNext
        }
    }

    suspend fun updateNetworkAndCache(timeoutSeconds: Int, imageCacheMb: Int) {
        context.argonDataStore.edit { prefs ->
            prefs[Keys.NETWORK_TIMEOUT] = timeoutSeconds.coerceIn(5, 60)
            prefs[Keys.IMAGE_CACHE_MB] = imageCacheMb.coerceIn(50, 1024)
        }
    }

    suspend fun exportSettingsJson(): String {
        val current = preferencesFlow.first()
        return json.encodeToString(current)
    }

    suspend fun importSettingsJson(rawJson: String): Boolean {
        val parsed = runCatching { json.decodeFromString<ArgonPreferences>(rawJson) }.getOrNull()
            ?: return false
        context.argonDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = parsed.darkTheme
            prefs[Keys.DYNAMIC_COLORS] = parsed.dynamicColors
            prefs[Keys.DEFAULT_PLAYER] = parsed.defaultPlayer.name
            prefs[Keys.AUDIO_LANG] = parsed.preferredAudioLanguage
            prefs[Keys.SUBTITLE_LANG] = parsed.preferredSubtitleLanguage
            prefs[Keys.SUBS_DISABLED] = parsed.subtitlesDisabledByDefault
            prefs[Keys.AUTO_NEXT_EPISODE] = parsed.autoPlayNextEpisode
            prefs[Keys.MAX_RESOLUTION] = parsed.preferredMaxResolution
            prefs[Keys.PREFER_CACHED] = parsed.preferCachedDebridStreams
            prefs[Keys.EXCLUDED_CODECS] = parsed.excludedCodecsCsv
            prefs[Keys.NETWORK_TIMEOUT] = parsed.networkTimeoutSeconds
            prefs[Keys.IMAGE_CACHE_MB] = parsed.imageCacheLimitMb
        }
        return true
    }

    suspend fun resetAllPreferences() {
        context.argonDataStore.edit { it.clear() }
    }
}