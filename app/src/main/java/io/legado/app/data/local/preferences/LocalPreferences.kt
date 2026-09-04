package io.legado.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.localDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_ui_status")

object LocalPreferencesKeys {
    val MIGRATED_TO_SETTINGS = booleanPreferencesKey("__local_ui_status_migrated_to_settings")
    val SHOW_THEME_REFACTOR_TIP = booleanPreferencesKey("show_theme_refactor_tip")
    val SEARCH_LAYOUT_MODE = intPreferencesKey("search_layout_mode")
    val SEARCH_SCOPE = stringPreferencesKey("search_scope")
    val MATCH_MODE = intPreferencesKey("match_mode")
    val READ_URL_IN_BROWSER = booleanPreferencesKey("read_url_in_browser")
    val LAST_BACKUP = longPreferencesKey("last_backup")
    val PASSWORD = stringPreferencesKey("password")
    val PERMISSION_CHECKED = booleanPreferencesKey("permission_checked")

    // ENABLE_READ_RECORD也许需要换个地方存，但先放这
    val ENABLE_READ_RECORD = booleanPreferencesKey("enableReadRecord")
    val READ_RECORD_DISPLAY_MODE = stringPreferencesKey("readRecordDisplayMode")
    val READ_RECORD_ALIAS_DECISIONS = stringPreferencesKey("readRecordAliasDecisions")
    val COVER_ALBUM_MIGRATED = booleanPreferencesKey("cover_album_migrated")
    val SELECTED_COVER_ALBUM_ID = stringPreferencesKey("selected_cover_album_id")
    val SELECTED_LIGHT_COVER_ALBUM_ID = stringPreferencesKey("selected_light_cover_album_id")
    val SELECTED_DARK_COVER_ALBUM_ID = stringPreferencesKey("selected_dark_cover_album_id")

    // Change source options
    val CHANGE_SOURCE_CHECK_AUTHOR = booleanPreferencesKey("changeSourceCheckAuthor")
    val CHANGE_SOURCE_LOAD_INFO = booleanPreferencesKey("changeSourceLoadInfo")
    val CHANGE_SOURCE_LOAD_TOC = booleanPreferencesKey("changeSourceLoadToc")
    val CHANGE_SOURCE_LOAD_WORD_COUNT = booleanPreferencesKey("changeSourceLoadWordCount")
    val CHANGE_SOURCE_SEARCH_SCOPE = stringPreferencesKey("changeSourceSearchScope")

    // Book source check options
    val CHECK_SOURCE_TIMEOUT = longPreferencesKey("checkSourceTimeout")
    val CHECK_SOURCE_SEARCH = booleanPreferencesKey("checkSearch")
    val CHECK_SOURCE_DISCOVERY = booleanPreferencesKey("checkDiscovery")
    val CHECK_SOURCE_INFO = booleanPreferencesKey("checkInfo")
    val CHECK_SOURCE_CATEGORY = booleanPreferencesKey("checkCategory")
    val CHECK_SOURCE_CONTENT = booleanPreferencesKey("checkContent")
}
