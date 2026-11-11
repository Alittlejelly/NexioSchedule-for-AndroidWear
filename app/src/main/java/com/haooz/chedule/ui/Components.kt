package com.haooz.chedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.R
import com.haooz.chedule.data.CourseSlot

/** 统一文本封装：单一入口控制省略与行数，避免各处 BasicText 参数散落 */
@Composable
fun Label(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign != null) style.copy(textAlign = textAlign) else style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 课程元信息：教师｜地点，缺一不可时空串 */
fun metaText(slot: CourseSlot): String = listOf(slot.course.teacher, slot.course.location)
    .filter { it.isNotBlank() }
    .joinToString("｜")

/** 卡片底部：第 9-10 节｜地点 */
fun sectionMetaText(slot: CourseSlot): String = listOf(slot.course.sectionText(), slot.course.location)
    .filter { it.isNotBlank() }
    .joinToString("｜")

fun stateLabel(state: CardState): String = when (state) {
    CardState.SOON -> "快上课了"
    CardState.ONGOING -> "正在上课"
    CardState.UPCOMING -> "即将开始"
    CardState.FINISHED -> "已结束"
}

/**
 * 主卡片（设计稿「按钮」）：顶部渐变 + #222 底，右上角喇叭 / 纸飞机。
 * 快上课用红色 + 喇叭，正在上课用蓝色 + 纸飞机。
 */
@Composable
fun HeroCard(
    slot: CourseSlot,
    state: CardState,
    nowMinute: Int,
    modifier: Modifier = Modifier,
) {
    val isRed = state == CardState.SOON
    val topColor = if (isRed) Tokens.HeroRed else Tokens.HeroBlue
    val accent = if (isRed) Tokens.AccentRed else Tokens.AccentBlue
    val iconRes = if (isRed) R.drawable.ic_megaphone else R.drawable.ic_plane

    val countdown = when (state) {
        CardState.SOON -> "还有 ${(slot.startMinute - nowMinute).coerceAtLeast(1)} 分钟"
        CardState.ONGOING -> "还有 ${(slot.endMinute - nowMinute).coerceAtLeast(1)} 分钟"
        else -> slot.timeRangeText
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.HeroRadius))
            .background(Tokens.Surface)
            .background(heroGradient(topColor)),
    ) {
        Column(
            modifier = Modifier.padding(start = 10.dp, top = 9.dp, end = 10.dp, bottom = 9.dp),
        ) {
            Label(
                text = stateLabel(state),
                style = Tokens.heroLabel(),
                modifier = Modifier.padding(end = 40.dp),
            )
            Spacer(Modifier.height(2.dp))
            Label(slot.course.name, Tokens.heroName(), maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Label(countdown, Tokens.heroCountdown(accent))
            val meta = metaText(slot)
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Label(meta, Tokens.heroMeta(), maxLines = 1)
            }
        }
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(39.dp),
        )
    }
}

/** 普通课程卡片（设计稿「复选/按钮」）：#222 底、22px 圆角 */
@Composable
fun CourseCard(
    slot: CourseSlot,
    state: CardState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.CardRadius))
            .background(Tokens.Surface)
            .padding(start = 7.dp, top = 5.dp, end = 4.dp, bottom = 5.dp),
    ) {
        Label(stateLabel(state), Tokens.cardLabel(state))
        Spacer(Modifier.height(1.dp))
        Label(slot.course.name, Tokens.cardName(), maxLines = 1)
        Spacer(Modifier.height(1.dp))
        Label(slot.timeRangeText, Tokens.cardTime())
        val meta = sectionMetaText(slot)
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(1.dp))
            Label(meta, Tokens.cardMeta(), maxLines = 1)
        }
    }
}

/** 时段标题：上午课程 / 下午课程 / 晚上课程 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(title, Tokens.sectionHeader(), textAlign = TextAlign.Center)
    }
}

/** 底部固定切换栏：‹ 第6周 周三 › */
@Composable
fun DaySwitchBar(
    dayText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black,
                    1f to Color.Black,
                )
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_caret_left),
                contentDescription = "前一天",
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onPrev)
                    .padding(8.dp),
            )
            Label(dayText, Tokens.barText(), modifier = Modifier.padding(horizontal = 2.dp))
            Image(
                painter = painterResource(R.drawable.ic_caret_right),
                contentDescription = "后一天",
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onNext)
                    .padding(8.dp),
            )
        }
    }
}

/** 圆形胶囊按钮：今 / 关于 */
@Composable
fun PillButton(
    text: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, Tokens.pillText(), textAlign = TextAlign.Center)
    }
}

/** 空态：今天没有课啦 */
@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label("没有课程啦", TextStyle(fontSize = 14.sp, color = Tokens.TextSecondary))
        Spacer(Modifier.height(2.dp))
        Label("好好休息吧", TextStyle(fontSize = 14.sp, color = Tokens.TextSecondary))
    }
}

/** 列表末尾操作：「关于」+（不在今天时）「今」并排；只有「关于」时居中 */
@Composable
fun ListActions(
    showToday: Boolean,
    onAbout: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton("关于", Tokens.Surface, onAbout)
        if (showToday) {
            Spacer(Modifier.width(10.dp))
            PillButton("今", Tokens.FabBlue, onToday)
        }
    }
}
