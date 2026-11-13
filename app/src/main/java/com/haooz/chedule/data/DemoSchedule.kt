package com.haooz.chedule.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 演示课表：生成一份合法的 v4 整表 JSON，用于没有手机推送时预览界面效果。
 *
 * 课程时间围绕「当前时刻」动态生成，保证任意时间点打开都能看到全部状态：
 * 已结束 / 正在上课（主卡蓝色）/ 快上课了（主卡红色窗口内）/ 课前倒计时 / 晚课。
 * 全部课程塞进「今日 override」，绕开周次推算，演示数据永不过期。
 */
object DemoSchedule {

    const val DEMO_NAME = "演示课表"

    fun buildJson(now: LocalTime = LocalTime.now()): String {
        val today = LocalDate.now()
        val nowMin = now.hour * 60 + now.minute

        fun course(
            id: String,
            name: String,
            start: Int,
            end: Int,
            teacher: String,
            location: String,
        ): JSONObject = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("dayOfWeek", today.dayOfWeek.value)
            .put("startSection", 0)
            .put("endSection", 0)
            .put("startWeek", 1)
            .put("endWeek", 20)
            .put("weekType", 0)
            .put("selectedWeeks", JSONArray())
            .put("isCustomTime", true)
            .put("customStartTime", formatHm(start.coerceAtLeast(0)))
            .put("customEndTime", formatHm(end))
            .put("location", location)
            .put("teacher", teacher)

        // 按开始时间排序：主卡取「第一个未结束的」
        val demo = listOf(
            course("demo-1", "高等数学", 8 * 60 + 10, 8 * 60 + 55, "王建国", "教学楼 A101"),
            course("demo-2", "大学英语", 9 * 60 + 5, 9 * 60 + 50, "李雯", "教学楼 A203"),
            course("demo-3", "数据结构", nowMin - 15, nowMin + 30, "张伟", "教学楼 B302"),
            course("demo-4", "操作系统", nowMin + 12, nowMin + 57, "刘洋", "教学楼 B303"),
            course("demo-5", "计算机网络", nowMin + 95, nowMin + 165, "赵敏", "实验楼 C101"),
            course("demo-6", "算法设计与分析", 19 * 60, 20 * 60 + 40, "", "教学楼 D201"),
        ).sortedBy { it.getString("customStartTime") }

        val monday = today.with(DayOfWeek.MONDAY)
        val payload = JSONObject()
            .put("protocol", SchedulePayload.PROTOCOL)
            .put("version", SchedulePayload.VERSION)
            .put("schedule_name", DEMO_NAME)
            .put("sentAt", System.currentTimeMillis())
            .put(
                "settings",
                JSONObject()
                    .put("class_start_time", monday.toString())
                    .put("current_week", 1)
                    .put("total_weeks", 20)
                    .put("morning_sections", 6)
                    .put("afternoon_sections", 6)
                    .put("evening_sections", 4)
                    .put("pre_class_reminder_minutes", 20),
            )
            .put(
                "times",
                JSONObject()
                    .put("morning", JSONObject().put("1", "08:10-08:55").put("2", "09:05-09:50"))
                    .put("afternoon", JSONObject().put("1", "14:00-15:40"))
                    .put("evening", JSONObject().put("1", "19:00-20:40")),
            )
            .put("courses", JSONArray(demo))
            .put("holidays", JSONArray())
            .put(
                "overrides",
                JSONArray().put(
                    JSONObject()
                        .put("date", today.toString())
                        .put("holiday", false)
                        .put("holidayName", "")
                        .put("week", 6)
                        .put("weekday", today.dayOfWeek.value)
                        .put("courses", JSONArray(demo)),
                ),
            )
        return payload.toString()
    }
}
