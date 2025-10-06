package com.apollomonasa.scheduleapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScheduleViewModel : ViewModel() {
    // 使用StateFlow来持有最新的排班结果
    private val _scheduleResult = MutableStateFlow<ScheduleResult?>(null)
    val scheduleResult = _scheduleResult.asStateFlow()

    // 用于更新排班结果的方法
    fun setScheduleResult(result: ScheduleResult) {
        _scheduleResult.value = result
    }
}