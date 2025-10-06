package com.apollomonasa.scheduleapp

import java.time.DayOfWeek

/**
 * 代表一个周循环的、具体的不可用时间点。
 */
data class WeeklyTimeSlot(
    val dayOfWeek: DayOfWeek,
    val session: Int
)

/**
 * 辅助类，用于存放初步解析信息，不包含周次。
 */
data class ParsedWeeklyTimeInfo(
    val dayOfWeek: DayOfWeek,
    val sessions: IntRange
)

/**
 * 代表一个需要被安排的班次（Shift）。
 */
data class Shift(
    val id: String, // 唯一ID，例如 "MONDAY_1_2"
    val dayOfWeek: DayOfWeek,
    val sessions: IntRange
) {
    val isNightShift: Boolean
        get() = sessions.contains(10) || sessions.contains(11)
}

/**
 * 代表一个可参与排班的人员及其约束。
 */
data class Participant(
    val person: Person,
    val unavailableSlots: Set<WeeklyTimeSlot>,
    val grade: String,
    val quota: Int, // 新增：该人员需要被安排的班次数
    val needsSeniorBuddy: Boolean,
    val canDoNightShift: Boolean
)

/**
 * 代表最终的排班结果。
 */
typealias ScheduleResult = Map<Shift, List<Participant>>


/**
 * 用于报告数据库操作的结果
 */
data class DbOperationResult(
    val newCount: Int,      // 新增人数
    val updatedCount: Int   // 更新人数
)

/**
 * 新增：一个专门用于JSON序列化的数据结构。
 * 它代表了排班结果中的一条记录。
 */
data class StorableScheduleEntry(
    val shift: Shift,
    val people: List<Participant>
)