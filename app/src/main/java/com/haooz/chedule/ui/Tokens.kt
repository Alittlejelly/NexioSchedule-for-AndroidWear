package com.haooz.chedule.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 卡片状态，决定标签文案与配色 */
enum class CardState { SOON, ONGOING, UPCOMING, FINISHED }

/**
 * 设计令牌，取自 Figma「Nexio课程表」node 97:99（480×480 圆屏）。
 *
 * 尺寸换算：设计稿以 480px 画布绘制，手表按 240dp 逻辑宽度渲染，
 * 因此 设计 px ÷ 2 = dp/sp。例如课程名 40px → 20sp，卡片圆角 32px → 16dp。
 */
object Tokens {

    // ===== 颜色 =====
    val Bg = Color.Black
    val Surface = Color(0xFF222222)

    val TextPrimary = Color.White
    val TextSecondary = Color(0xBFFFFFFF)   // rgba(255,255,255,0.75)
    val TextTertiary = Color(0x80FFFFFF)    // rgba(255,255,255,0.5)
    val TextBar = Color(0xCCFFFFFF)         // rgba(255,255,255,0.8)

    val AccentBlue = Color(0xFF6CAFFF)
    val AccentRed = Color(0xFFFF6C6C)

    /** 快上课了：linear-gradient(180deg, rgba(218,89,60,1), rgba(102,0,0,0)) */
    val HeroRed = Color(0xFFDA593C)
    /** 正在上课：linear-gradient(180deg, rgba(21,128,255,1), rgba(0,47,102,0)) */
    val HeroBlue = Color(0xFF1580FF)

    val FabBlue = Color(0xFF0A84FF)
    val Connected = Color(0xFF1E7A3C)
    val Disconnected = Color(0xFF8A5A00)

    // 关于页状态卡（Figma 102:82）：琥珀色渐变 + #FFC160 高亮
    val StatusWarnTop = Color(0xFFD79123)
    val StatusWarnBottom = Color(0xFF232221)
    val StatusWarnAccent = Color(0xFFFFC160)
    val StatusOkTop = Color(0xFF2E7D4F)
    val StatusOkAccent = Color(0xFF7BE09B)

    // ===== 尺寸 =====
    val HeroRadius = 16.dp      // 32px
    val CardRadius = 11.dp      // 22px
    val ScreenPaddingH = 8.dp
    val ContentBottomPadding = 30.dp
    /** 底部日期切换栏高度（覆盖层，内容要自己让开） */
    val BarHeight = 46.dp

    // ===== 字号 =====
    fun clock() = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    fun date() = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    fun heroLabel() = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
    fun heroName() = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    fun heroCountdown(accent: Color) =
        TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = accent)
    fun heroMeta() = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = TextSecondary)

    fun cardLabel(state: CardState) = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (state == CardState.FINISHED) TextTertiary else TextSecondary,
    )
    fun cardName() = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    fun cardTime() = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AccentBlue)
    fun cardMeta() = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = TextSecondary)

    fun sectionHeader() = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = TextBar,
        letterSpacing = 0.2.em,
    )
    fun barText() = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextBar)
    fun pillText() = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
}

/** 竖向渐变：顶部满色，底部全透明，叠在 #222 之上 */
fun heroGradient(top: Color) = Brush.verticalGradient(
    0f to top,
    1f to top.copy(alpha = 0f),
)
