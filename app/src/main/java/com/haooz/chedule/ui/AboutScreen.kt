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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.TimeText
import com.haooz.chedule.R
import com.haooz.chedule.data.SyncMeta
import com.haooz.chedule.update.AppUpdate
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    versionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    // ItemCenter 锚点下，首项顶部落在「屏幕半高 − 项高/2 − scrollOffset」处。
    // 默认开屏把首项滚到屏幕正中，顶部空出一大块；这里给显式初始位置：
    // 首项（标题，高 30dp）顶部正好在 58dp，即曲面时间正下方。
    val initialOffsetPx = with(density) {
        (config.screenHeightDp.dp / 2 - 73.dp).toPx().roundToInt()
    }
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = initialOffsetPx,
    )

    Box(modifier = modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Tokens.ScreenPaddingH,
                end = Tokens.ScreenPaddingH,
                top = 58.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(),
            // 关掉 autoCentering：它会在列表头部插占位 Spacer，抢占 index 0，
            // 使上面的 initialCenterItemIndex = 0 指向 Spacer 而不是标题
            autoCentering = null,
            // 表冠滚动：默认触觉反馈依赖 Google Wear 系统库（小米手表5上不存在，会闪退），
            // hapticFeedbackEnabled = false 绕开它；详见 HomeScreen 同款注释
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(
                listState,
                hapticFeedbackEnabled = false,
            ),
        ) {
            // 「应用状态」标题跟随列表滚动
            item { AboutHeader(onBack = onBack) }

            item { AppIdentity(versionName) }

            item {
                StatusCard(
                    lastSyncAt = meta.lastReceivedAt,
                    error = meta.lastError,
                )
            }

            item { SectionHeader("版本更新") }
            item { UpdateCard(versionName) }

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
                    HowLine("在手机端设置-导入导出-导出中导入课表")
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

        // 贴合屏幕上边缘的曲面时间（覆盖层，不参与列表滚动）
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** 标题栏（列表首项，跟随滚动）：箭头紧贴标题左侧，整组居中；末尾补等宽透明占位让标题落在屏幕中线 */
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

/**
 * 状态卡：渐变底 + 时间 + 右上徽标（Figma #102:62）。
 * BLE 连接只在推送课表那几秒真实存在，所以不显示「已连接/未连接」瞬时态：
 * 只要连接过（收到过一次课表）就整卡绿色 + 对勾，标题恒为「最近一次连接」，
 * 时间为最近一次收到课表的时刻；从未收到过才是琥珀色 + 印章。
 */
@Composable
private fun StatusCard(lastSyncAt: Long, error: String) {
    val everConnected = lastSyncAt > 0
    val topColor = if (everConnected) Tokens.StatusOkTop else Tokens.StatusWarnTop
    val bottomColor = Tokens.StatusWarnBottom
    val accent = if (everConnected) Tokens.StatusOkAccent else Tokens.StatusWarnAccent

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
                "最近一次连接",
                TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary),
            )
            Spacer(Modifier.height(2.dp))
            Label(
                syncTimeText(lastSyncAt),
                TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = accent),
            )
            // 从未连接过时把失败原因直接摆出来：是没发现手机、还是拉取失败，
            // 否则只有一个 --:--，出问题只能靠猜。
            if (!everConnected && error.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Label(
                    error,
                    TextStyle(fontSize = 9.sp, color = Tokens.TextTertiary),
                    maxLines = 2,
                )
            }
        }
        Image(
            painter = painterResource(if (everConnected) R.drawable.ic_check else R.drawable.ic_seal),
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

/** 版本更新卡片的界面状态 */
private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class HasUpdate(val info: AppUpdate.Info) : UpdateUi
    data object UpToDate : UpdateUi
    data class Downloading(val progress: Int) : UpdateUi
    data class Ready(val file: File) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

/**
 * 在线更新卡：检查更新（Gitee 优先、GitHub 兜底）→ 显示新版本 → 下载（带进度）→ 唤起系统安装器。
 */
@Composable
private fun UpdateCard(currentVersionName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var installPromptedFor by remember { mutableStateOf<File?>(null) }

    // 下载完成后自动唤起一次安装器；失败时用户仍可点「安装」重试
    LaunchedEffect(ui) {
        val file = (ui as? UpdateUi.Ready)?.file
        if (file != null && installPromptedFor != file) {
            installPromptedFor = file
            runCatching { AppUpdate.install(context, file) }
        }
    }

    fun check() {
        ui = UpdateUi.Checking
        scope.launch {
            ui = withContext(Dispatchers.IO) {
                AppUpdate.fetchLatest(context).fold(
                    onSuccess = { info ->
                        info?.let(UpdateUi::HasUpdate) ?: UpdateUi.UpToDate
                    },
                    onFailure = { UpdateUi.Failed(it.message ?: "检查失败") },
                )
            }
        }
    }

    fun download(info: AppUpdate.Info) {
        ui = UpdateUi.Downloading(0)
        scope.launch {
            ui = withContext(Dispatchers.IO) {
                AppUpdate.downloadApk(context, info) { progress ->
                    ui = UpdateUi.Downloading(progress)
                }.fold(
                    onSuccess = { UpdateUi.Ready(it) },
                    onFailure = { UpdateUi.Failed(it.message ?: "下载失败") },
                )
            }
        }
    }

    val statusText = when (val s = ui) {
        UpdateUi.Idle -> "当前 v$currentVersionName"
        UpdateUi.Checking -> "正在检查更新…"
        is UpdateUi.HasUpdate ->
            "发现新版本 v${s.info.versionName}（来自 ${s.info.source}）" +
                if (s.info.notes.isNotBlank()) "\n${s.info.notes}" else ""
        UpdateUi.UpToDate -> "已是最新版本 v$currentVersionName"
        is UpdateUi.Downloading -> "正在下载… ${s.progress}%"
        is UpdateUi.Ready -> "下载完成，请在弹出的安装器中确认"
        is UpdateUi.Failed -> s.message
    }

    val actionLabel: String? = when (val s = ui) {
        UpdateUi.Idle, UpdateUi.UpToDate, is UpdateUi.Failed -> "检查更新"
        is UpdateUi.HasUpdate -> "下载更新"
        is UpdateUi.Ready -> "安装"
        else -> null // Checking / Downloading 进行中，不给按钮
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.CardRadius))
            .background(Tokens.Surface)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Label(
            statusText,
            TextStyle(fontSize = 12.sp, color = Tokens.TextSecondary),
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
        actionLabel?.let { label ->
            Spacer(Modifier.height(6.dp))
            PillButton(
                label,
                Tokens.FabBlue,
                onClick = {
                    when (val s = ui) {
                        is UpdateUi.HasUpdate -> download(s.info)
                        else -> check()
                    }
                },
            )
        }
    }
}
