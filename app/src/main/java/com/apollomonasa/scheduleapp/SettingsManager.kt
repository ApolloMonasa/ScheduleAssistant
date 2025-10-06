package com.apollomonasa.scheduleapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 通过委托创建DataStore实例
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        // 定义一个Key来存储班次设置，我们将其存储为字符串集合
        val SHIFTS_KEY = stringSetPreferencesKey("custom_shifts")
    }

    // 保存班次设置
    suspend fun saveShifts(shifts: Set<String>) {
        dataStore.edit { settings ->
            settings[SHIFTS_KEY] = shifts
        }
    }

    // 读取班次设置的Flow
    // 默认值是我们的标准班次
    val shiftsFlow: Flow<Set<String>> = dataStore.data
        .map { preferences ->
            preferences[SHIFTS_KEY] ?: setOf(
                "MONDAY,1,2", "MONDAY,3,5", "MONDAY,6,7", "MONDAY,8,9", "MONDAY,10,11",
                "TUESDAY,1,2", "TUESDAY,3,5", "TUESDAY,6,7", "TUESDAY,8,9", "TUESDAY,10,11",
                "WEDNESDAY,1,2", "WEDNESDAY,3,5", "WEDNESDAY,6,7", "WEDNESDAY,8,9", "WEDNESDAY,10,11",
                "THURSDAY,1,2", "THURSDAY,3,5", "THURSDAY,6,7", "THURSDAY,8,9", "THURSDAY,10,11",
                "FRIDAY,1,2", "FRIDAY,3,5", "FRIDAY,6,7", "FRIDAY,8,9", "FRIDAY,10,11"
            )
        }
}