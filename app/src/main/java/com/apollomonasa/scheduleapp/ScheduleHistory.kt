package com.apollomonasa.scheduleapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_history")
data class ScheduleHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long, // 排班生成的时间戳
    val scheduleJson: String // 存储排班结果的JSON字符串
)