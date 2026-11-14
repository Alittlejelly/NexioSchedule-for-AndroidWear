package com.haooz.chedule.ui

import androidx.activity.compose.BackHandler
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

/** 版本更新的界面状态 */
private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class HasUpdate(val info: AppUpdate.Info) : UpdateUi
    data object UpToDate : UpdateUi
    data class Downloading(val progress: Int) : UpdateUi
    data class Ready(val file: File) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

// 配色取自设计稿（画板 29/32）：卡片黑→蓝渐变，按钮深蓝渐变
private val UpdateCardTop = Color(0xFF3381FF)
private val UpdateCardBottom = Color.Black
private val UpdatePillTop = Color(0xFF175EC6)
private val UpdatePillBottom = Color(0xFF1C4D93)

/** 蓝渐变胶囊按钮（设计稿「检查更新」样式） */
@Composable
private fun UpdatePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(0f to UpdatePillTop, 1f to UpdatePillBottom))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(
            text,
            TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White),
        )
    }
}

/**
 * 在线更新（设计稿：画板29 卡片 / 画板30 箭头徽标 / 画板32 详情页）。
 * 检查更新（Gitee 优先、GitHub 兜底）→ 发现新版本进入全屏更新日志页 → 下载（带进度）→ 唤起安装器。
 */
@Composable
private fun UpdateCard(currentVersionName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var showDetail by remember { mutableStateOf(false) }
    var installPromptedFor by remember { mutableStateOf<File?>(null) }
    // 已是最新时记录远程 Info：update.json 的 notes 就是当前安装版本的更新日志，
    // 点徽标/卡片可进入详情页查看（与有新版本时同一个页面）
    var currentLog by remember { mutableStateOf<AppUpdate.Info?>(null) }

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
                        if (info.versionCode > AppUpdate.currentVersionCode(context)) {
                            showDetail = true
                            UpdateUi.HasUpdate(info)
                        } else {
                            // 已是最新：update.json 描述的就是本版本的日志，记下来供详情页展示
                            currentLog = info
                            UpdateUi.UpToDate
                        }
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

    // —— 详情页（画板 32）：发现新版本后的全屏「版本更新 / 更新日志」 ——
    if (showDetail) {
        BackHandler(enabled = true) { showDetail = false }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(34.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_caret_left),
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { showDetail = false }
                        .padding(6.dp),
                )
                Spacer(Modifier.width(2.dp))
                Label(
                    "版本更新",
                    TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary),
                )
            }
            Spacer(Modifier.height(14.dp))

            // 有新版本时显示新版本日志；已是最新时显示当前安装版本的更新日志
            val hasUpdate = ui is UpdateUi.HasUpdate || ui is UpdateUi.Downloading || ui is UpdateUi.Ready
            val info = (ui as? UpdateUi.HasUpdate)?.info ?: currentLog
            if (info != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Tokens.CardRadius))
                        .background(Tokens.Surface)
                        .padding(10.dp),
                ) {
                    Label(
                        if (hasUpdate) {
                            "新版本 v${info.versionName}（来自 ${info.source}）"
                        } else {
                            "当前版本 v${info.versionName} · 已是最新"
                        },
                        TextStyle(fontSize = 12.sp, color = Tokens.TextPrimary),
                    )
                    Spacer(Modifier.height(4.dp))
                    Label(
                        "更新日志",
                        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary),
                    )
                    Spacer(Modifier.height(2.dp))
                    Label(
                        info.notes.ifBlank { "性能优化与问题修复" },
                        TextStyle(fontSize = 11.sp, color = Tokens.TextSecondary),
                        maxLines = 8,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 进度条 + 状态/按钮区
            when (val s = ui) {
                is UpdateUi.Downloading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Tokens.Surface),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(s.progress / 100f)
                                .height(5.dp)
                                .background(Brush.horizontalGradient(0f to UpdatePillTop, 1f to UpdateCardTop)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Label(
                        "正在下载… ${s.progress}%",
                        TextStyle(fontSize = 11.sp, color = Tokens.TextSecondary),
                    )
                }
                is UpdateUi.Ready -> {
                    Label(
                        "下载完成，请在安装器中确认",
                        TextStyle(fontSize = 11.sp, color = Tokens.TextSecondary),
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    UpdatePill("安装", onClick = { runCatching { AppUpdate.install(context, s.file) } })
                }
                is UpdateUi.HasUpdate -> {
                    UpdatePill("下载并安装", onClick = { download(s.info) })
                }
                else -> {}
            }
            Spacer(Modifier.height(24.dp))
        }
        return
    }

    // —— 卡片（画板 29）：黑→蓝渐变 + 当前版本 + 检查更新 + 箭头徽标 ——
    // 徽标可点击：有新版本或已检查过（拿到当前版本日志）时进入详情页
    val canOpenDetail = ui is UpdateUi.HasUpdate || currentLog != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(0f to UpdateCardTop, 1f to UpdateCardBottom)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 9.dp, end = 12.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Label(
                    "当前版本",
                    TextStyle(fontSize = 12.sp, color = Color.White),
                )
                Spacer(Modifier.height(2.dp))
                Label(
                    "v$currentVersionName",
                    TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
                )
                when (val s = ui) {
                    UpdateUi.Checking -> {
                        Spacer(Modifier.height(3.dp))
                        Label(
                            "正在检查更新…",
                            TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f)),
                        )
                    }
                    UpdateUi.UpToDate -> {
                        Spacer(Modifier.height(3.dp))
                        Label(
                            "已是最新版本",
                            TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f)),
                        )
                    }
                    is UpdateUi.Failed -> {
                        Spacer(Modifier.height(3.dp))
                        Label(
                            s.message,
                            TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f)),
                            maxLines = 2,
                        )
                    }
                    else -> {}
                }
                Spacer(Modifier.height(6.dp))
                UpdatePill("检查更新", onClick = { check() })
            }
            // 右上箭头徽标（画板 30，渐变与透明度已烘焙进素材）
            Image(
                painter = painterResource(R.drawable.ic_update_badge),
                contentDescription = "查看更新日志",
                modifier = Modifier
                    .size(46.dp)
                    .then(
                        if (canOpenDetail) {
                            Modifier.clickable { showDetail = true }
                        } else {
                            Modifier
                        }
                    ),
            )
        }
    }
}
