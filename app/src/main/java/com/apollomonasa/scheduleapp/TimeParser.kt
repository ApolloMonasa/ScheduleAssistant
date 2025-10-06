package com.apollomonasa.scheduleapp

import java.time.DayOfWeek
import java.util.regex.Pattern

object TimeParser {

    // 主入口函数：返回 Set<WeeklyTimeSlot>
    fun parseAllClassTimes(allTimesString: String): Set<WeeklyTimeSlot> {
        val unavailableSlots = mutableSetOf<WeeklyTimeSlot>()
        val individualClassStrings = allTimesString.split(';').filter { it.isNotBlank() }

        for (classString in individualClassStrings) {
            try {
                val parsedInfoList = parseSingleClassString(classString)
                parsedInfoList.forEach { parsedInfo ->
                    for (session in parsedInfo.sessions) {
                        unavailableSlots.add(
                            WeeklyTimeSlot(
                                dayOfWeek = parsedInfo.dayOfWeek,
                                session = session
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                println("Warning: Skipping malformed time string: '$classString' due to ${e.message}")
            }
        }
        return unavailableSlots
    }

    // 正则表达式只捕获星期和节次，完全忽略周次{...}
    private fun parseSingleClassString(classString: String): List<ParsedWeeklyTimeInfo> {
        val results = mutableListOf<ParsedWeeklyTimeInfo>()
        // 这个正则表达式是关键：它只匹配到“节”字为止
        val mainPattern = Pattern.compile("""星期([一二三四五六日]{1,7})第(.+?)节""")
        val matcher = mainPattern.matcher(classString.trim())

        if (!matcher.find()) {
            throw IllegalArgumentException("Invalid class string format: $classString")
        }

        val dayOfWeekStr = matcher.group(1)
        val sessionsStr = matcher.group(2)

        val days = parseDays(dayOfWeekStr)
        val sessions = parseSessions(sessionsStr)

        for (day in days) {
            for (sessionRange in sessions) {
                results.add(ParsedWeeklyTimeInfo(day, sessionRange))
            }
        }
        return results
    }

    private fun parseDays(daysStr: String): List<DayOfWeek> {
        return daysStr.map {
            when (it) {
                '一' -> DayOfWeek.MONDAY
                '二' -> DayOfWeek.TUESDAY
                '三' -> DayOfWeek.WEDNESDAY
                '四' -> DayOfWeek.THURSDAY
                '五' -> DayOfWeek.FRIDAY
                '六' -> DayOfWeek.SATURDAY
                '日' -> DayOfWeek.SUNDAY
                else -> throw IllegalArgumentException("Invalid day char: $it")
            }
        }
    }

    private fun parseSessions(sessionsStr: String): List<IntRange> {
        return sessionsStr.split(',').map { part ->
            val rangeParts = part.split('-')
            val start = rangeParts[0].toInt()
            val end = rangeParts.getOrNull(1)?.toInt() ?: start
            start..end
        }
    }
}