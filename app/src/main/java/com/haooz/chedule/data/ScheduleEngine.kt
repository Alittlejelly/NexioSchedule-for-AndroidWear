package com.haooz.chedule.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 课程在当天的实时状态 */
enum class CoursePhase { UPCOMING, SOON, ONGOING, FINISHED }

/** 分钟数 → "HH:mm" */
fun formatHm(minute: Int): String {
    val safe = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(safe / 60, safe % 60)
}

/** 已解析出具体时刻的一节课 */
data class CourseSlot(
    val course: Course,
    val startMinute: Int,
    val endMinute: Int,
) {
    val startText: String get() = formatHm(startMinute)
    val endText: String get() = formatHm(endMinute)
    val timeRangeText: String get() = "$startText - $endText"
}

/** 某一天的最终裁决结果 */
data class DaySchedule(
    val date: LocalDate,
    val week: Int,
    val weekday: Int,          // 1=周一 … 7=周日
    val holiday: Boolean,
    val holidayName: String,
    val slots: List<CourseSlot>,
    /** true 表示命中手机端下发的逐日裁决表，未走本地推算 */
    val fromOverride: Boolean,
)

/**
 * 课表推算引擎。
 *
 * 与手环端 `src/common/schedule.js` 的行为保持一致：
 * - 周次 = floorDiv(开学日到当天天数 + (开学日星期-1), 7) + 1，再用 current_week 校准偏移（±2 内吸收）
 * - 周次过滤与 Course.isActiveInWeek 严格一致
 * - 命中 overrides 的日期整日采用手机端裁决，不再自行推算
 */
object ScheduleEngine {

    /** 开学日；解析失败返回 null */
    fun termStart(payload: SchedulePayload): LocalDate? {
        val raw = payload.settings.classStartTime.trim()
        if (raw.isEmpty()) return null
        val normalized = raw.replace('/', '-').replace('.', '-')
        return runCatching { LocalDate.parse(normalized) }.getOrNull()
    }

    /** 纯日历周次（未校准），与手机端 calendarWeekForDate 一致 */
    private fun calendarWeek(start: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(start, date)
        val leading = start.dayOfWeek.value - 1
        return Math.floorDiv(days + leading, 7L).toInt() + 1
    }

    /**
     * 校准偏移：调休合并周会让日历周与手机教学周错开。
     * 只用 current_week 做一次性校准，偏差超过 ±2 视为异常数据（不采信）。
     */
    fun weekOffset(payload: SchedulePayload, today: LocalDate = LocalDate.now()): Int {
        val current = payload.settings.currentWeek
        if (current <= 0) return 0
        val start = termStart(payload) ?: return 0
        val delta = current - calendarWeek(start, today)
        return if (delta in -2..2) delta else 0
    }

    /** 指定日期的教学周；无开学日时返回 0 */
    fun weekFor(
        date: LocalDate,
        payload: SchedulePayload,
        offset: Int = weekOffset(payload),
    ): Int {
        val start = termStart(payload) ?: return 0
        return (calendarWeek(start, date) + offset).coerceAtLeast(0)
    }

    /** 该日期是否落在某个假期区间内 */
    private fun holidayOf(date: LocalDate, payload: SchedulePayload): HolidayEntry? {
        val key = date.toString()
        for (entry in payload.holidays) {
            if (entry.date.isBlank()) continue
            // 补班日（配置了 followDate 的目标日）不算假期
            if (entry.followDate == key) continue
            val start = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: continue
            val end = if (entry.endDate.isBlank()) start else {
                runCatching { LocalDate.parse(entry.endDate) }.getOrNull() ?: start
            }
            if (!date.isBefore(start) && !date.isAfter(end)) return entry
        }
        return null
    }

    /** 解析某一天：优先使用手机端下发的逐日裁决，缺失时本地推算 */
    fun resolve(
        date: LocalDate,
        payload: SchedulePayload,
        offset: Int = weekOffset(payload),
    ): DaySchedule {
        val key = date.toString()

        // 1) 逐日裁决表命中 —— 手机端今日页的最终结果，直接采用
        payload.overrides[key]?.let { ov ->
            val weekday = if (ov.weekday in 1..7) ov.weekday else date.dayOfWeek.value
            return DaySchedule(
                date = date,
                week = ov.week,
                weekday = weekday,
                holiday = ov.holiday,
                holidayName = ov.holidayName,
                slots = ov.courses.toSlots(payload).sortedBy { it.startMinute },
                fromOverride = true,
            )
        }

        // 2) 本地推算
        val week = weekFor(date, payload, offset)
        val weekday = date.dayOfWeek.value
        val holiday = holidayOf(date, payload)
        val slots = if (holiday != null) {
            emptyList()
        } else {
            payload.courses
                .filter { it.dayOfWeek == weekday && it.isActiveInWeek(week) }
                .toSlots(payload)
                .sortedBy { it.startMinute }
        }
        return DaySchedule(
            date = date,
            week = week,
            weekday = weekday,
            holiday = holiday != null,
            holidayName = holiday?.name ?: "",
            slots = slots,
            fromOverride = false,
        )
    }

    /** 把课程换算成带具体时刻的槽位；无有效时间的课程丢弃（无法排序与倒计时） */
    private fun List<Course>.toSlots(payload: SchedulePayload): List<CourseSlot> =
        mapNotNull { course ->
            val startMin: Int?
            val endMin: Int?
            if (course.hasCustomTime) {
                startMin = parseHm(course.customStartTime)
                endMin = parseHm(course.customEndTime)
            } else {
                startMin = parseHm(payload.sectionTimes[course.startSection]?.substringBefore("-")?.trim() ?: "")
                endMin = parseHm(payload.sectionTimes[course.endSection]?.substringAfter("-")?.trim() ?: "")
            }
            if (startMin == null || endMin == null || endMin <= startMin) {
                null
            } else {
                CourseSlot(course, startMin, endMin)
            }
        }

    /** 课程状态：快上课 / 正在上课 / 已结束 / 未开始 */
    fun phaseOf(slot: CourseSlot, nowMinute: Int, leadMinutes: Int): CoursePhase = when {
        nowMinute < slot.startMinute ->
            if (slot.startMinute - nowMinute <= leadMinutes) CoursePhase.SOON else CoursePhase.UPCOMING
        nowMinute < slot.endMinute -> CoursePhase.ONGOING
        else -> CoursePhase.FINISHED
    }

    /** 今天还有几节课（尚未结束的都算） */
    fun remainingCount(slots: List<CourseSlot>, nowMinute: Int): Int =
        slots.count { it.endMinute > nowMinute }

    fun parseHm(text: String): Int? {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun nowMinute(now: LocalTime = LocalTime.now()): Int = now.hour * 60 + now.minute

    fun weekdayText(weekday: Int): String = when (weekday) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; else -> "周日"
    }

    fun dateText(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    /** 顶部时间：9:28（不补零），与设计稿一致 */
    fun clockText(now: LocalTime = LocalTime.now()): String = "${now.hour}:%02d".format(now.minute)
}
