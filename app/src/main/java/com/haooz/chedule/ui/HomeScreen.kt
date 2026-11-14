package com.haooz.chedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.TimeText
import com.haooz.chedule.data.CoursePhase
import com.haooz.chedule.data.CourseSlot
import com.haooz.chedule.data.DaySchedule
import com.haooz.chedule.data.ScheduleEngine
import com.haooz.chedule.data.SchedulePayload
import com.haooz.chedule.data.ScheduleSettings
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** 日期可翻页范围：以今天为轴，前后各一年 */
private const val DAY_RANGE = 365

/**
 * 主界面（圆屏 480×480）。
 *
 * - 顶部时间用 Wear 官方 [TimeText]，沿屏幕圆弧贴合边缘，随分钟自动刷新
 * - 左右横滑切换日期：用 [HorizontalPager] 承载，滑动即翻页，自带跟手与惯性动画；
 *   底部箭头、悬浮「今」按钮统一走 animateScrollToPage，动画曲线一致
 * - 有课列表用 [ScalingLazyColumn]（缩放手感），但修正默认初始定位并关闭 autoCentering；
 *   表冠滚动可用（触觉关闭以规避小米手表5缺失的 com.google.wear.input 系统类）
 */
@Composable
fun HomeScreen(
    payload: SchedulePayload?,
    now: LocalTime,
    onAbout: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = DAY_RANGE) { DAY_RANGE * 2 + 1 }
    val offset = remember(payload) {
        payload?.let { ScheduleEngine.weekOffset(it) } ?: 0
    }

    val currentDate = today.plusDays((pagerState.currentPage - DAY_RANGE).toLong())
    val currentDay = remember(payload, currentDate, offset) {
        payload?.let { ScheduleEngine.resolve(currentDate, it, offset) }
    }
    fun goTo(page: Int) {
        scope.launch {
            pagerState.animateScrollToPage(page.coerceIn(0, DAY_RANGE * 2))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val date = today.plusDays((page - DAY_RANGE).toLong())
            DayPage(
                payload = payload,
                date = date,
                now = now,
                offset = offset,
                onAbout = onAbout,
                onToday = { goTo(DAY_RANGE) },
            )
        }

        // 贴合屏幕上边缘的曲面时间
        TimeText(modifier = Modifier.align(Alignment.TopCenter))

        DaySwitchBar(
            dayText = dayBarText(currentDay),
            onPrev = { goTo(pagerState.currentPage - 1) },
            onNext = { goTo(pagerState.currentPage + 1) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 单日内容页：日期头 + 主卡片 + 分时段课程列表 */
@Composable
private fun DayPage(
    payload: SchedulePayload?,
    date: LocalDate,
    now: LocalTime,
    offset: Int,
    onAbout: () -> Unit,
    onToday: () -> Unit,
) {
    val settings = payload?.settings ?: ScheduleSettings()
    val day = remember(payload, date, offset) {
        payload?.let { ScheduleEngine.resolve(date, it, offset) }
    }
    val nowMinute = ScheduleEngine.nowMinute(now)
    val isToday = date == LocalDate.now()
    val lead = settings.preClassMinutes

    val slots = day?.slots.orEmpty()
    // 无课（含未同步）：不走列表，改成整页 Column —— 列表内容是「有多少排多少」，
    // 内容少时「关于」会被留在半空，看着像被空白顶起来了。这里用权重撑开，
    // 空态文案居中、「关于」贴到底部切换栏上方。
    if (slots.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Tokens.ScreenPaddingH),
        ) {
            // 顶部留白保持原值（与曲面时间的关系是既定视觉，不算 bug）
            Spacer(Modifier.height(26.dp))
            DateHeader(
                dateText = ScheduleEngine.dateText(date),
                subtitle = headerSubtitle(day, isToday, nowMinute),
            )
            Spacer(Modifier.weight(1f))
            EmptyState()
            Spacer(Modifier.weight(1f))
            ListActions(showToday = !isToday, onAbout = onAbout, onToday = onToday)
            // 让开底部日期切换栏（覆盖层，46dp）
            Spacer(Modifier.height(Tokens.BarHeight))
        }
        return
    }

    // ScalingLazyColumn（保留卡片随滚动缩放的手感），但修正两个默认行为：
    // 1) 初始定位：rememberScalingLazyListState 默认把 index 1 滚到屏幕正中，日期头会被挤出顶部；
    //    这里显式给 initialCenterItemIndex = 0 + 偏移，让首项（日期头）落在曲面时间下方。
    //    ItemCenter 锚点下首项顶部 = 屏幕半高 − 首项半高 − scrollOffset，
    //    即 scrollOffset = 半高 − 26dp(顶部让位) − 17dp(日期头半高，约 34dp 高)。
    // 2) autoCentering = null：内容不满一屏时不再整段垂直居中。
    // 表冠触觉必须关掉：默认实现依赖 com.google.wear.input 系统库，小米手表5上没有（详见 Haptics.kt）。
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = with(density) {
            (config.screenHeightDp.dp / 2 - 43.dp).toPx().roundToInt()
        },
    )
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Tokens.ScreenPaddingH,
            end = Tokens.ScreenPaddingH,
            top = 26.dp,
            bottom = Tokens.ContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(),
        autoCentering = null,
        rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(
            listState,
            hapticFeedbackEnabled = false,
        ),
    ) {
        item {
            DateHeader(
                dateText = ScheduleEngine.dateText(date),
                subtitle = headerSubtitle(day, isToday, nowMinute),
            )
        }

        val hero = if (isToday) {
            slots.firstOrNull {
                ScheduleEngine.phaseOf(it, nowMinute, lead) != CoursePhase.FINISHED
            }
        } else {
            null
        }

        if (hero != null) {
            item {
                HeroCard(
                    slot = hero,
                    state = heroState(hero, nowMinute, lead),
                    nowMinute = nowMinute,
                )
            }
        }

        val rest = if (hero != null) slots - hero else slots
        val grouped = rest.groupBy { periodOf(it) }
        for (period in listOf("上午课程", "下午课程", "晚上课程")) {
            val list = grouped[period].orEmpty()
            if (list.isEmpty()) continue
            item { SectionHeader(period) }
            items(list) { slot ->
                CourseCard(
                    slot = slot,
                    state = cardState(slot, nowMinute, lead, isToday),
                    statusText = cardStatusLabel(slot, cardState(slot, nowMinute, lead, isToday), nowMinute, isToday),
                )
            }
        }

        // 列表末尾：「关于」与「今」并排；回到今天时「今」不渲染
        item { ListActions(showToday = !isToday, onAbout = onAbout, onToday = onToday) }
    }
}

/** 日期 + 剩余课数（时间已由顶部 TimeText 承担） */
@Composable
private fun DateHeader(dateText: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(dateText, Tokens.date(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(1.dp))
        Label(subtitle, Tokens.date(), textAlign = TextAlign.Center)
    }
}

private fun headerSubtitle(day: DaySchedule?, isToday: Boolean, nowMinute: Int): String {
    if (day == null) return "尚未同步课表"
    if (isToday) {
        if (day.holiday) return day.holidayName.ifBlank { "今天放假" }
        val remaining = ScheduleEngine.remainingCount(day.slots, nowMinute)
        return when {
            day.slots.isEmpty() -> "今天没有课啦"
            remaining <= 0 -> "今天的课都上完啦"
            else -> "今天还有 $remaining 节课"
        }
    }
    if (day.holiday) return day.holidayName.ifBlank { "放假" }
    return if (day.slots.isEmpty()) "没有课程" else "共 ${day.slots.size} 节课"
}

private fun dayBarText(day: DaySchedule?): String {
    if (day == null) return "未同步"
    val weekday = ScheduleEngine.weekdayText(day.weekday)
    return if (day.week > 0) "第${day.week}周 $weekday" else weekday
}

private fun heroState(slot: CourseSlot, nowMinute: Int, lead: Int): CardState =
    when (ScheduleEngine.phaseOf(slot, nowMinute, lead)) {
        CoursePhase.SOON -> CardState.SOON
        CoursePhase.ONGOING -> CardState.ONGOING
        CoursePhase.UPCOMING -> CardState.UPCOMING
        CoursePhase.FINISHED -> CardState.FINISHED
    }

private fun cardState(slot: CourseSlot, nowMinute: Int, lead: Int, isToday: Boolean): CardState {
    if (!isToday) return CardState.UPCOMING
    return when (ScheduleEngine.phaseOf(slot, nowMinute, lead)) {
        CoursePhase.SOON -> CardState.SOON
        CoursePhase.ONGOING -> CardState.ONGOING
        CoursePhase.UPCOMING -> CardState.UPCOMING
        CoursePhase.FINISHED -> CardState.FINISHED
    }
}

/** 课程卡状态行（对齐手环端卡片）：未开始 / 上课中 / 已结束，非今天统一「未开始」；倒计时只出现在主卡 */
private fun cardStatusLabel(
    slot: CourseSlot,
    state: CardState,
    nowMinute: Int,
    isToday: Boolean,
): String = when {
    !isToday -> "未开始"
    state == CardState.ONGOING -> "上课中"
    state == CardState.FINISHED -> "已结束"
    else -> "未开始"
}

private fun periodOf(slot: CourseSlot): String = when {
    slot.startMinute < 12 * 60 -> "上午课程"
    slot.startMinute < 18 * 60 -> "下午课程"
    else -> "晚上课程"
}
