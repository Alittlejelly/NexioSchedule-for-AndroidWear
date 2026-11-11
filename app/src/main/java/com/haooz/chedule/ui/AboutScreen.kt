package com.haooz.chedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.TimeText
import com.haooz.chedule.R
import com.haooz.chedule.data.SyncMeta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 关于页 / 应用状态。
 *
 * 内容按 Vela 版（MI Band Pro 手环端）的关于页逐项对齐：
 *   应用状态头 → 应用信息 → 手机连接状态卡 → 如何使用 → 开发人员名单 → 打赏支持
 * 视觉按 Figma 102:82 的圆屏规范：顶部曲面时间、白色圆形图标、琥珀色渐变状态卡。
 *
 * 版本号由调用方从 PackageManager 读入（不依赖生成的 BuildConfig，
 * 避免 IDE 索引未同步时出现找不到 BuildConfig 的假报错）。
 */
@Composable
fun AboutScreen(
    meta: SyncMeta,
    connected: Boolean,
    versionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberScalingLazyListState()
    // 顶部固定区高度实测值：不用魔数猜 TimeText 有多高，量到多少就让出多少
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }

    Box(modifier = modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Tokens.ScreenPaddingH,
                end = Tokens.ScreenPaddingH,
                // 顶部固定区（曲面时间 + 标题）不再随列表滚动，内容直接从它下方开始
                top = if (headerHeightPx > 0) headerHeight + 4.dp else 58.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(),
            // 关掉自动居中：内容不满一屏时默认会整段垂直居中，顶部留一大块空白
            autoCentering = null,
            rotaryScrollableBehavior = null,
        ) {
            item { AppIdentity(versionName) }

            item {
                StatusCard(
                    connected = connected,
                    lastSyncAt = meta.lastReceivedAt,
                    error = meta.lastError,
                )
            }

            item { SectionHeader("如何使用") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Tokens.CardRadius))
                        .background(Tokens.Surface)
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HowLine("安卓手机下载")
                    HowLine("Nexio课程表")
                    HowLine("连接后使用")
                }
            }

            item { SectionHeader("开发人员名单") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Tokens.CardRadius))
                        .background(Tokens.Surface)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    CreditRow(R.drawable.avatar_1, "一個小果冻", "快应用开发设计")
                    CreditRow(R.drawable.avatar_3, "安龙 Aurysian", "快应用 UI 设计")
                    CreditRow(R.drawable.avatar_2, "Sunlight", "原应用开发设计")
                }
            }

            item { SectionHeader("打赏支持") }
            item { RewardCard() }
        }

        // 顶部固定区：曲面时间 + 标题栏。
        // 不放进列表是因为 Wear 的 ScalingLazyColumn 没有 stickyHeader，放进去就会跟着滚走。
        // 底部铺一段同色渐变，让滚上来的内容在标题下方淡出，不会与标题撞在一起。
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onGloballyPositioned { headerHeightPx = it.size.height }
                .background(
                    Brush.verticalGradient(
                        0f to Tokens.Bg,
                        0.72f to Tokens.Bg,
                        1f to Color.Transparent,
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimeText()
            AboutHeader(onBack = onBack)
        }
    }
}

/** 固定标题栏：箭头紧贴标题左侧，整组居中；末尾补等宽透明占位让标题落在屏幕中线 */
@Composable
private fun AboutHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_caret_left),
            contentDescription = "返回",
            modifier = Modifier
                .size(30.dp)
                .clickable(onClick = onBack)
                .padding(8.dp),
        )
        Spacer(Modifier.width(2.dp))
        Label(
            "应用状态",
            TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Tokens.TextPrimary),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(32.dp))
    }
}

@Composable
private fun AppIdentity(versionName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.about_icon_round),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Label(
                "Nexio课程表",
                TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary),
            )
            Label(
                "v$versionName",
                TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Tokens.TextTertiary),
            )
        }
    }
}

/**
 * 打赏卡（照 Vela 版 .rw-card）：
 * 一张白色圆角卡，二维码整块贴在上方，底部是绿色弧形横带（rw_band 上缘自带弧线），
 * 「微信」白色粗体压在带上。
 *
 * 注意不要写成「二维码在左、绿带在右」的横向排布——绿带是给白卡收口的，
 * 放在二维码旁边会像一块飘着的绿色方块，也就是之前看到的错位。
 */
@Composable
private fun RewardCard() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(144.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.img_reward_qr),
                contentDescription = "微信赞赏码",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(104.dp)
                    .aspectRatio(232f / 252f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(59.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Image(
                    painter = painterResource(R.drawable.rw_band),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Label(
                    "微信",
                    TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HowLine(text: String) {
    Label(
        text,
        TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Tokens.TextPrimary),
        modifier = Modifier.padding(vertical = 1.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CreditRow(avatarRes: Int, name: String, role: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Label(name, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Tokens.TextPrimary))
            Label(role, TextStyle(fontSize = 10.sp, color = Tokens.TextSecondary))
        }
    }
}

/** 状态卡：渐变底 + 大号状态 + 高亮时间 + 右上徽标（Figma #102:62） */
@Composable
private fun StatusCard(connected: Boolean, lastSyncAt: Long, error: String) {
    val topColor = if (connected) Tokens.StatusOkTop else Tokens.StatusWarnTop
    val bottomColor = Tokens.StatusWarnBottom
    val accent = if (connected) Tokens.StatusOkAccent else Tokens.StatusWarnAccent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.verticalGradient(0f to topColor, 1f to bottomColor)),
    ) {
        Column(
            modifier = Modifier.padding(start = 9.dp, top = 7.dp, end = 9.dp, bottom = 9.dp),
        ) {
            Label(
                if (connected) "手机已连接" else "手机未连接",
                TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary),
            )
            Spacer(Modifier.height(2.dp))
            Label(
                syncTimeText(lastSyncAt),
                TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = accent),
            )
            Label(
                "最后一次本地更新时间",
                TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Tokens.TextSecondary),
            )
            // 未连接时把失败原因直接摆出来：是没发现手机、还是拉取失败，
            // 否则只有一个「手机未连接」，出问题只能靠猜。
            if (!connected && error.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Label(
                    error,
                    TextStyle(fontSize = 9.sp, color = Tokens.TextTertiary),
                    maxLines = 2,
                )
            }
        }
        Image(
            painter = painterResource(if (connected) R.drawable.ic_check else R.drawable.ic_seal),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 6.dp)
                .size(53.dp),
        )
    }
}

private val syncFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun syncTimeText(lastSyncAt: Long): String {
    if (lastSyncAt <= 0) return "--:--"
    return runCatching {
        Instant.ofEpochMilli(lastSyncAt)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(syncFormatter)
    }.getOrDefault("--:--")
}
