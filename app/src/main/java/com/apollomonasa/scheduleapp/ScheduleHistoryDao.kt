package com.apollomonasa.scheduleapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleHistoryDao {
    @Insert
    suspend fun insert(history: ScheduleHistory)

    @Query("SELECT * FROM schedule_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ScheduleHistory>>

    @Query("DELETE FROM schedule_history")
    suspend fun clearAll()
}