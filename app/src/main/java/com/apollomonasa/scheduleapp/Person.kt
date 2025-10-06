package com.apollomonasa.scheduleapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people") // 定义表名为 "people"
data class Person(
    @PrimaryKey // 将学号设置为主键，确保唯一性
    val studentId: String,

    val name: String,

    // 存储的是聚合后的一长串原始上课时间
    val allClassTimes: String
)