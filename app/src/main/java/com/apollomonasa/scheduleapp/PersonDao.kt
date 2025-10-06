package com.apollomonasa.scheduleapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    // 插入或更新一条人员信息。如果已存在相同studentId，则替换它。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: Person)

    // 批量插入或更新
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(people: List<Person>)

    // 查询所有人员信息，并按学号排序
    // 返回一个Flow，当数据变化时，UI可以自动收到更新
    @Query("SELECT * FROM people ORDER BY studentId ASC")
    fun getAllPeople(): Flow<List<Person>>

    @Query("SELECT studentId FROM people")
    suspend fun getAllStudentIds(): List<String>

    @Query("SELECT * FROM people WHERE studentId = :id")
    suspend fun getPersonById(id: String): Person?

    @Query("DELETE FROM people WHERE studentId = :id")
    suspend fun deletePersonById(id: String)
}