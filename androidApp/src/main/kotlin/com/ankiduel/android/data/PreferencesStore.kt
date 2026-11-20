package com.ankiduel.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ankiduel.shared.engine.AntiIdleValidator
import com.ankiduel.shared.model.GlobalLockStatus
import com.ankiduel.shared.model.SocialAppCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "ankiduel_prefs")

data class StoredSession(
    val userId: String?,
    val username: String?,
    val email: String?,
    val demoMode: Boolean,
    val trackedSocialPackages: Set<String>
)

class PreferencesStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val EMAIL = stringPreferencesKey("email")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val TRACKED_PACKAGES = stringSetPreferencesKey("tracked_social_packages")

        val IDLE_DAY_KEY = stringPreferencesKey("idle_day_key")
        val IDLE_LAST_RAW_ANKI = longPreferencesKey("idle_last_raw_anki")
        val IDLE_LAST_CARDS = intPreferencesKey("idle_last_cards")
        val IDLE_SINCE_REVIEW = longPreferencesKey("idle_since_review")
        val IDLE_EFFECTIVE_ACC = longPreferencesKey("idle_effective_acc")

        val CACHED_LOCK_STATUS_JSON = stringPreferencesKey("cached_lock_status_json")
        val CACHED_LOCK_TRACKED_PACKAGES = stringSetPreferencesKey("cached_lock_tracked_packages")
    }

    val sessionFlow: Flow<StoredSession> = context.dataStore.data.map { prefs ->
        StoredSession(
            userId = prefs[Keys.USER_ID],
            username = prefs[Keys.USERNAME],
            email = prefs[Keys.EMAIL],
            demoMode = prefs[Keys.DEMO_MODE] ?: false,
            trackedSocialPackages = prefs[Keys.TRACKED_PACKAGES] ?: SocialAppCatalog.defaultSelectedPackages
        )
    }.distinctUntilChanged()

    suspend fun getSession(): StoredSession = sessionFlow.first()

    suspend fun saveAuth(userId: String, username: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USERNAME] = username
            prefs[Keys.EMAIL] = email
            prefs[Keys.DEMO_MODE] = false
        }
    }

    suspend fun enableDemoMode(username: String = "Moi") {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEMO_MODE] = true
            prefs[Keys.USER_ID] = "local-demo-user"
            prefs[Keys.USERNAME] = username
            prefs.remove(Keys.EMAIL)
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.EMAIL)
            prefs[Keys.DEMO_MODE] = false
        }
    }

    suspend fun setTrackedPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TRACKED_PACKAGES] = packages
        }
    }

    suspend fun loadAntiIdleState(todayKey: String = LocalDate.now().toString()): AntiIdleValidator.State {
        val prefs = context.dataStore.data.first()
        val storedDay = prefs[Keys.IDLE_DAY_KEY]
        if (storedDay != null && storedDay != todayKey) {
            return AntiIdleValidator.State()
        }
        return AntiIdleValidator.State(
            lastRawAnkiSeconds = prefs[Keys.IDLE_LAST_RAW_ANKI] ?: 0L,
            lastCardsReviewed = prefs[Keys.IDLE_LAST_CARDS] ?: 0,
            secondsSinceLastReview = prefs[Keys.IDLE_SINCE_REVIEW] ?: 0L,
            accumulatedEffectiveSeconds = prefs[Keys.IDLE_EFFECTIVE_ACC] ?: 0L
        )
    }

    suspend fun saveAntiIdleState(
        state: AntiIdleValidator.State,
        todayKey: String = LocalDate.now().toString()
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IDLE_DAY_KEY] = todayKey
            prefs[Keys.IDLE_LAST_RAW_ANKI] = state.lastRawAnkiSeconds
            prefs[Keys.IDLE_LAST_CARDS] = state.lastCardsReviewed
            prefs[Keys.IDLE_SINCE_REVIEW] = state.secondsSinceLastReview
            prefs[Keys.IDLE_EFFECTIVE_ACC] = state.accumulatedEffectiveSeconds
        }
    }

    suspend fun resetAntiIdleState() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.IDLE_DAY_KEY)
            prefs.remove(Keys.IDLE_LAST_RAW_ANKI)
            prefs.remove(Keys.IDLE_LAST_CARDS)
            prefs.remove(Keys.IDLE_SINCE_REVIEW)
            prefs.remove(Keys.IDLE_EFFECTIVE_ACC)
        }
    }

    suspend fun saveCachedLockState(status: GlobalLockStatus, trackedPackages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_LOCK_STATUS_JSON] = json.encodeToString(status)
            prefs[Keys.CACHED_LOCK_TRACKED_PACKAGES] = trackedPackages
        }
    }

    suspend fun loadCachedLockState(): Pair<GlobalLockStatus, Set<String>>? {
        val prefs = context.dataStore.data.first()
        val raw = prefs[Keys.CACHED_LOCK_STATUS_JSON] ?: return null
        val status = runCatching { json.decodeFromString<GlobalLockStatus>(raw) }.getOrNull() ?: return null
        val tracked = prefs[Keys.CACHED_LOCK_TRACKED_PACKAGES] ?: SocialAppCatalog.defaultSelectedPackages
        return status to tracked
    }

    suspend fun clearCachedLockState() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CACHED_LOCK_STATUS_JSON)
            prefs.remove(Keys.CACHED_LOCK_TRACKED_PACKAGES)
        }
    }
}
