package dev.sfpixel.hcdashboard.handlers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {
    companion object {
        val BIRTH_DATE_KEY = stringPreferencesKey("birth_date")
    }

    val birthDate: Flow<LocalDate?> = context.dataStore.data.map { preferences ->
        preferences[BIRTH_DATE_KEY]?.let {
            LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    suspend fun saveBirthDate(date: LocalDate) {
        context.dataStore.edit { preferences ->
            preferences[BIRTH_DATE_KEY] = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }
}
