package com.apollomonasa.scheduleapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. 在 @Database 注解中加入新的Entity，并把 version 升级到 2
@Database(entities = [Person::class, ScheduleHistory::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun scheduleHistoryDao(): ScheduleHistoryDao // 2. 添加新的DAO抽象方法

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedule_database"
                )
                    // 3. 数据库升级时，采用破坏性迁移（清空旧数据）
                    // 这是最简单的迁移方式，对于我们的App是可接受的
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}