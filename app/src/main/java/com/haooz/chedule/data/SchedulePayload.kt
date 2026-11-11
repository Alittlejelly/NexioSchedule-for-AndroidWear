package com.haooz.chedule.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 手表端课表数据模型。
 *
 * 字段与手机端 `WatchPayload.buildFullJson()`（协议 version=4 整表）一一对应，
 * 跨端改动必须两边同步。
 */
data class Course(
    val id: String = "",
    val name: String = "",
    val dayOfWeek: Int = 1,          // 1=周一 … 7=周日
    val startSection: Int = 0,
    val endSection: Int = 0,
    val startWeek: Int = 0,
    val endWeek: Int = 0,
    val weekType: Int = 0,           // 0=全周 1=单周 2=双周
    val selectedWeeks: List<Int> = emptyList(),
    val isCustomTime: Boolean = false,
    val customStartTime: String = "",
    val customEndTime: String = "",
    val location: String = "",
    val teacher: String = "",
) {
    /** 与手机端 Course.isActiveInWeek 严格一致 */
    fun isActiveInWeek(week: Int): Boolean {
        if (selectedWeeks.isNotEmpty()) return week in selectedWeeks
        if (week < startWeek || week > endWeek) return false
        return when (weekType) {
            WEEK_TYPE_ODD -> week % 2 == 1
            WEEK_TYPE_EVEN -> week % 2 == 0
            else -> true
        }
    }

    val hasCustomTime: Boolean
        get() = isCustomTime &&
            customStartTime.isNotBlank() &&
            customEndTime.isNotBlank()

    /** "第 9-10 节" / "第 9 节"；无节次返回空串 */
    fun sectionText(): String {
        if (startSection <= 0 && endSection <= 0) return ""
        return if (startSection == endSection) {
            "第 ${startSection} 节"
        } else {
            "第 $startSection-$endSection 节"
        }
    }

    companion object {
        const val WEEK_TYPE_ALL = 0
        const val WEEK_TYPE_ODD = 1
        const val WEEK_TYPE_EVEN = 2
    }
}

/** 假期 / 调休条目（字段与手机端 HolidayManager.Entry 对齐，宽松解析） */
data class HolidayEntry(
    val date: String = "",
    val endDate: String = "",
    val name: String = "",
    val type: String = "",
    /** 调休目标绝对日期；非空表示「这天要上 followDate 那天的课」 */
    val followDate: String = "",
)

/** v4.1 逐日裁决覆盖表：命中即整日采用，不再自行推算 */
data class DayOverride(
    val date: String,
    val holiday: Boolean,
    val holidayName: String,
    val week: Int,
    /** ISO 域 1=周一 … 7=周日 */
    val weekday: Int,
    val courses: List<Course>,
)

data class ScheduleSettings(
    val classStartTime: String = "",   // "YYYY/MM/DD"
    val currentWeek: Int = 0,
    val totalWeeks: Int = 0,
    val morningSections: Int = 6,
    val afternoonSections: Int = 6,
    val eveningSections: Int = 4,
    /** 「快上课了」提前量，手机端默认 20；缺失时手表用 15 */
    val preClassMinutes: Int = 15,
)

data class SchedulePayload(
    val version: Int = 0,
    val scheduleName: String = "",
    val sentAt: Long = 0L,
    val settings: ScheduleSettings = ScheduleSettings(),
    /** 绝对节次号 -> "HH:mm-HH:mm"（已把相对节次换算为全局节次） */
    val sectionTimes: Map<Int, String> = emptyMap(),
    val courses: List<Course> = emptyList(),
    val holidays: List<HolidayEntry> = emptyList(),
    val overrides: Map<String, DayOverride> = emptyMap(),
) {
    companion object {
        const val PROTOCOL = "nexio.schedule"
        const val VERSION = 4

        /** 解析整表 JSON；非本协议 / 版本不支持 / 结构损坏时返回 null */
        fun parse(text: String): SchedulePayload? = runCatching {
            val root = JSONObject(text)
            if (root.optString("protocol") != PROTOCOL) return null
            val version = root.optInt("version", 0)
            if (version < VERSION) return null

            val settingsJson = root.optJSONObject("settings") ?: JSONObject()
            val morning = settingsJson.optInt("morning_sections", 6)
            val afternoon = settingsJson.optInt("afternoon_sections", 6)
            val evening = settingsJson.optInt("evening_sections", 4)
            val settings = ScheduleSettings(
                classStartTime = settingsJson.optString("class_start_time", ""),
                currentWeek = settingsJson.optInt("current_week", 0),
                totalWeeks = settingsJson.optInt("total_weeks", 0),
                morningSections = morning,
                afternoonSections = afternoon,
                eveningSections = evening,
                preClassMinutes = settingsJson.optInt("pre_class_reminder_minutes", 15),
            )

            val courses = root.optJSONArray("courses").toCourseList()
            val overrides = LinkedHashMap<String, DayOverride>()
            root.optJSONArray("overrides")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val date = o.optString("date", "")
                    if (date.isBlank()) continue
                    overrides[date] = DayOverride(
                        date = date,
                        holiday = o.optBoolean("holiday", false),
                        holidayName = o.optString("holidayName", ""),
                        week = o.optInt("week", 0),
                        weekday = o.optInt("weekday", 0),
                        courses = o.optJSONArray("courses").toCourseList(),
                    )
                }
            }

            SchedulePayload(
                version = version,
                scheduleName = root.optString("schedule_name", ""),
                sentAt = root.optLong("sentAt", 0L),
                settings = settings,
                sectionTimes = buildSectionTimes(settings, root.optJSONObject("times")),
                courses = courses,
                holidays = root.optJSONArray("holidays").toHolidayList(),
                overrides = overrides,
            )
        }.getOrNull()

        /**
         * 相对节次 -> 绝对节次。协议里 times 按上午/下午/晚上分组各自从 1 开始编号，
         * 手表内部统一用绝对节次（与手机端 Course.startSection 同坐标系）。
         */
        private fun buildSectionTimes(
            settings: ScheduleSettings,
            times: JSONObject?,
        ): Map<Int, String> {
            val result = LinkedHashMap<Int, String>()
            if (times == null) return result

            fun putGroup(group: String, offset: Int) {
                val obj = times.optJSONObject(group) ?: return
                for (key in obj.keys()) {
                    val relative = key.toIntOrNull() ?: continue
                    val value = obj.optString(key, "")
                    if (value.isNotBlank()) result[offset + relative] = value
                }
            }

            val morningCount = settings.morningSections.coerceAtLeast(1)
            val afternoonCount = settings.afternoonSections.coerceAtLeast(1)
            putGroup("morning", 0)
            putGroup("afternoon", morningCount)
            putGroup("evening", morningCount + afternoonCount)
            return result
        }

        private fun JSONArray?.toCourseList(): List<Course> {
            if (this == null) return emptyList()
            val list = ArrayList<Course>(length())
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                val weeks = ArrayList<Int>()
                o.optJSONArray("selectedWeeks")?.let { arr ->
                    for (j in 0 until arr.length()) weeks.add(arr.optInt(j, 0))
                }
                list.add(
                    Course(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        dayOfWeek = o.optInt("dayOfWeek", 0),
                        startSection = o.optInt("startSection", 0),
                        endSection = o.optInt("endSection", 0),
                        startWeek = o.optInt("startWeek", 0),
                        endWeek = o.optInt("endWeek", 0),
                        weekType = o.optInt("weekType", 0),
                        selectedWeeks = weeks.filter { it > 0 },
                        isCustomTime = o.optBoolean("isCustomTime", false),
                        customStartTime = o.optString("customStartTime", ""),
                        customEndTime = o.optString("customEndTime", ""),
                        location = o.optString("location", ""),
                        teacher = o.optString("teacher", ""),
                    )
                )
            }
            return list
        }

        private fun JSONArray?.toHolidayList(): List<HolidayEntry> {
            if (this == null) return emptyList()
            val list = ArrayList<HolidayEntry>(length())
            for (i in 0 until length()) {
                val o = optJSONObject(i) ?: continue
                list.add(
                    HolidayEntry(
                        date = o.optString("date", ""),
                        endDate = o.optString("endDate", ""),
                        name = o.optString("name", ""),
                        type = o.optString("type", ""),
                        followDate = o.optString("followDate", ""),
                    )
                )
            }
            return list
        }
    }
}
