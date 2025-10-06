package com.apollomonasa.scheduleapp

import android.util.Log
import java.time.DayOfWeek

object Scheduler {
    private const val TAG = "SchedulerAlgorithm"

    fun generateSchedule(
        allParticipants: List<Person>,
        gradeRules: List<GradeRule>,
        shifts: List<Shift>
    ): ScheduleResult {
        Log.d(TAG, "--- 开始生成排班表 (V12.0 绝对保底优先) ---")

        val participants = prepareParticipants(allParticipants, gradeRules)
        if (participants.isEmpty() || shifts.isEmpty()) return emptyMap()

        val schedule = shifts.associateWith { mutableListOf<Participant>() }
        val assignmentCounts = participants.associateWith { 0 }.toMutableMap()

        val highestGradeNeedingBuddy = participants
            .filter { it.needsSeniorBuddy }
            .mapNotNull { it.grade.toIntOrNull() }
            .maxOrNull() ?: 0

        // --- 阶段一：绝对保底覆盖 (不惜代价填满空班) ---
        Log.d(TAG, "--- 阶段一：绝对保底覆盖 ---")
        coverageFill(shifts, participants, schedule, assignmentCounts)

        // --- 阶段二：迭代式优化，以满足配额和均衡为目标 ---
        Log.d(TAG, "--- 阶段二：迭代式优化填充 ---")
        refineAndFill(schedule, participants, shifts, assignmentCounts, highestGradeNeedingBuddy)

        logFinalSchedule(schedule, shifts, assignmentCounts, participants)
        return schedule
    }

    private fun coverageFill(
        shifts: List<Shift>,
        participants: List<Participant>,
        schedule: Map<Shift, MutableList<Participant>>,
        assignmentCounts: MutableMap<Participant, Int>
    ) {
        for (shift in shifts.shuffled()) { // 打乱顺序以获得不同结果
            if (schedule[shift]?.isEmpty() == true) {
                // 第一轮尝试：寻找今天还没班的“理想人选”
                var candidate = participants.find { p ->
                    p.isAvailableFor(shift) &&
                            schedule.none { (s, people) -> s.dayOfWeek == shift.dayOfWeek && people.contains(p) }
                }

                // 第二轮尝试（紧急征用）：如果找不到，就忽略“每日一班”，找任何一个有空的人
                if (candidate == null) {
                    Log.w(TAG, "保底警告: 班次 ${shift.id} 找不到今日无班的人, 尝试紧急征用...")
                    candidate = participants.find { p -> p.isAvailableFor(shift) }
                }

                if (candidate != null) {
                    schedule[shift]?.add(candidate)
                    assignmentCounts[candidate] = (assignmentCounts[candidate] ?: 0) + 1
                    Log.d(TAG, "保底：将 ${candidate.person.name} 放入 ${shift.id}")
                } else {
                    Log.e(TAG, "错误: 班次 ${shift.id} 没有任何可用的候选人！")
                }
            }
        }
    }

    private fun refineAndFill(
        schedule: Map<Shift, MutableList<Participant>>,
        participants: List<Participant>,
        shifts: List<Shift>,
        assignmentCounts: MutableMap<Participant, Int>,
        highestGradeNeedingBuddy: Int
    ) {
        val totalAssignmentsNeeded = participants.sumOf { it.quota }
        var iteration = 0
        val maxIterations = totalAssignmentsNeeded * 2 // 设置一个迭代上限防止死循环

        while (iteration < maxIterations) {
            iteration++

            // 检查是否所有人都已满足配额
            val peopleWithRemainingQuota = participants.filter { (assignmentCounts[it] ?: 0) < it.quota }
            if (peopleWithRemainingQuota.isEmpty()) {
                Log.d(TAG, "所有人员配额已满足，优化结束于第 $iteration 轮。")
                break
            }

            var bestMove: Pair<Participant, Shift>? = null
            var maxScore = Double.NEGATIVE_INFINITY

            // 寻找最佳的“加人”操作
            for (person in peopleWithRemainingQuota) {
                for (shift in shifts) {
                    if (isValidAssignment(person, shift, schedule)) {
                        val score = calculateScoreForAdding(person, shift, schedule, highestGradeNeedingBuddy)
                        if (score > maxScore) {
                            maxScore = score
                            bestMove = Pair(person, shift)
                        }
                    }
                }
            }

            if (bestMove != null) {
                val (person, shift) = bestMove
                schedule[shift]?.add(person)
                assignmentCounts[person] = (assignmentCounts[person] ?: 0) + 1
                Log.d(TAG, "优化迭代 $iteration: 将 ${person.person.name} 添加到 ${shift.id} (得分: $maxScore)")
            } else {
                Log.w(TAG, "在第 $iteration 轮优化中，找不到任何有效的加人操作，优化结束。")
                break
            }
        }
    }

    private fun isValidAssignment(
        candidate: Participant,
        shift: Shift,
        currentSchedule: Map<Shift, List<Participant>>
    ): Boolean {
        if (!candidate.isAvailableFor(shift)) return false
        if (currentSchedule[shift]?.contains(candidate) == true) return false
        if (shift.isNightShift && !candidate.canDoNightShift) return false

        val isScheduledToday = currentSchedule.any { (s, people) ->
            s.dayOfWeek == shift.dayOfWeek && people.contains(candidate)
        }
        if (isScheduledToday) return false

        return true
    }

    private fun calculateScoreForAdding(person: Participant, shift: Shift, schedule: Map<Shift, List<Participant>>, highestGradeNeedingBuddy: Int): Double {
        var score = 1000.0

        // 奖励：满足高年级陪同
        if (person.needsSeniorBuddy) {
            if (schedule[shift]?.any { it.isSenior(highestGradeNeedingBuddy) } == true) {
                score += 200.0
            }
        } else if (person.isSenior(highestGradeNeedingBuddy)) {
            val juniorsInShift = schedule[shift]?.filter { it.needsSeniorBuddy } ?: emptyList()
            if (juniorsInShift.isNotEmpty() && juniorsInShift.none { j -> schedule[shift]?.any { s -> s.isSeniorTo(j) } == true }) {
                score += 150.0
            }
        }

        // 惩罚：人数越多，得分越低，这是实现均衡的关键
        score -= (schedule[shift]?.size ?: 0) * 50.0

        return score
    }

    private fun logFinalSchedule(
        schedule: Map<Shift, List<Participant>>,
        shifts: List<Shift>,
        assignmentCounts: Map<Participant, Int>,
        participants: List<Participant>
    ) {
        Log.d(TAG, "--- 最终班次人数统计 ---")
        shifts.sortedBy { it.dayOfWeek.value * 100 + it.sessions.first }.forEach { shift ->
            Log.d(TAG, "班次 ${shift.id.padEnd(20)} 人数: ${schedule[shift]?.size ?: 0} -> ${schedule[shift]?.joinToString(", ") { p -> p.person.name + "(${p.grade})" }}")
        }
        Log.d(TAG, "--- 人员配额满足情况 ---")
        participants.sortedBy { it.person.name }.forEach{ p ->
            val status = if ((assignmentCounts[p] ?: 0) >= p.quota) "OK" else "!!"
            Log.d(TAG, "${p.person.name.padEnd(5)}: ${assignmentCounts[p]}/${p.quota} [$status]")
        }
    }

    private fun prepareParticipants(
        people: List<Person>,
        gradeRules: List<GradeRule>
    ): List<Participant> {
        return people.map { person ->
            val unavailableSlots = TimeParser.parseAllClassTimes(person.allClassTimes)
            val grade = person.studentId.take(2)
            val rule = gradeRules.find { it.grade == grade }

            Participant(
                person = person,
                unavailableSlots = unavailableSlots,
                grade = grade,
                quota = rule?.shiftsPerWeek?.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                needsSeniorBuddy = rule?.needsSeniorBuddy ?: false,
                canDoNightShift = rule?.canDoNightShift ?: true
            )
        }
    }

    private fun Participant.isAvailableFor(shift: Shift): Boolean {
        return shift.sessions.all { session ->
            this.unavailableSlots.none { it.dayOfWeek == shift.dayOfWeek && it.session == session }
        }
    }

    private fun Participant.isSenior(highestGradeNeedingBuddy: Int): Boolean {
        val myGrade = this.grade.toIntOrNull() ?: 99
        if (highestGradeNeedingBuddy == 0) return false
        return myGrade < highestGradeNeedingBuddy
    }

    private fun Participant.isSeniorTo(other: Participant): Boolean {
        return (this.grade.toIntOrNull() ?: 99) < (other.grade.toIntOrNull() ?: 0)
    }
}