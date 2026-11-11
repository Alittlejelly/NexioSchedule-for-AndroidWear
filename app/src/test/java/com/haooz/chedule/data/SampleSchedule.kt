package com.haooz.chedule.data

import java.time.LocalDate

/**
 * 仅测试用的示例数据（原内置课表已从 App 中移除，避免用户把示例误当成自己的课表）。
 *
 * 只用于离屏截图核对视觉，不参与打包。
 * 开学日按「5 周前」推算，保证渲染时落在第 6 周、当天有课。
 */
object SampleSchedule {

    fun build(): SchedulePayload {
        val today = LocalDate.now()
        val weekday = today.dayOfWeek.value
        val termStart = today.minusWeeks(5)

        fun course(
            name: String,
            start: String,
            end: String,
            teacher: String,
            room: String,
            sectionStart: Int,
            sectionEnd: Int,
        ) = Course(
            id = "$name-$start",
            name = name,
            dayOfWeek = weekday,
            startSection = sectionStart,
            endSection = sectionEnd,
            startWeek = 1,
            endWeek = 20,
            weekType = Course.WEEK_TYPE_ALL,
            isCustomTime = true,
            customStartTime = start,
            customEndTime = end,
            location = room,
            teacher = teacher,
        )

        return SchedulePayload(
            version = SchedulePayload.VERSION,
            scheduleName = "示例课表",
            sentAt = System.currentTimeMillis(),
            settings = ScheduleSettings(
                classStartTime = "%d/%02d/%02d".format(
                    termStart.year, termStart.monthValue, termStart.dayOfMonth
                ),
                currentWeek = 6,
                totalWeeks = 20,
                morningSections = 4,
                afternoonSections = 4,
                eveningSections = 4,
                preClassMinutes = 20,
            ),
            sectionTimes = emptyMap(),
            courses = listOf(
                course("高等数学", "08:00", "09:40", "张明", "A10-201", 1, 2),
                course("单片机应用及原理", "14:00", "15:40", "果冻老师", "A10-310", 5, 6),
                course("数字电子技术", "19:25", "20:10", "安龙 Auryasian", "B2-105", 9, 10),
            ),
            holidays = emptyList(),
            overrides = emptyMap(),
        )
    }
}
