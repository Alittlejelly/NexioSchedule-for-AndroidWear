package com.haooz.chedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import com.haooz.chedule.data.ScheduleStore
import com.haooz.chedule.sync.BleScheduleServer
import com.haooz.chedule.sync.WatchSync
import com.haooz.chedule.ui.AboutScreen
import com.haooz.chedule.ui.HomeScreen
import com.haooz.chedule.ui.Label
import com.haooz.chedule.ui.PillButton
import com.haooz.chedule.ui.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchSync.init(this)

        setContent {
            // 权限申请放在 Compose 里：Activity 字段式 registerForActivityResult 会触发
            // lint 的 InvalidFragmentVersionForActivityResult（本工程不含 fragment，属误报），
            // 而 rememberLauncherForActivityResult 走的是 activity-compose 内部注册，不生这个告警。
            val context = LocalContext.current
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                // 拿到（或被拒）都试一次启动；拒绝时 start 内部会记日志并返回 false，本地缓存仍可看
                BleScheduleServer.start(context)
            }

            LaunchedEffect(Unit) {
                if (BleScheduleServer.hasPermissions(context)) {
                    BleScheduleServer.start(context)
                } else {
                    permissionLauncher.launch(BleScheduleServer.requiredPermissions())
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(Tokens.Bg)) {
                NexioApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从后台回来时广播可能已被系统停掉，补一次（start 内部幂等）
        if (BleScheduleServer.hasPermissions(this)) BleScheduleServer.start(this)
    }
}

@Composable
fun NexioApp() {
    val snapshot by ScheduleStore.state.collectAsState()

    // 上次崩溃日志：启动时异步读出，读到则盖一层反馈卡（复制 / 继续启动）
    var lastCrash by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val appContext = LocalContext.current
    LaunchedEffect(Unit) {
        val crash = withContext(Dispatchers.IO) { CrashReporter.consume(appContext) }
        if (!crash.isNullOrBlank()) lastCrash = crash
    }

    var showAbout by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalTime.now()) }

    // 版本号从 PackageManager 读，不依赖 BuildConfig（生成类在 IDE 索引未同步时会误报找不到）
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    // 15 秒一跳：「还有 X 分钟」与状态卡片要跟着时间走
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(15_000L)
        }
    }

    val scope = rememberCoroutineScope()

    /**
     * 关于页的层级进度：0 = 完全盖住主界面，1 = 完全滑出屏幕右侧。
     *
     * 推入、退出、跟手拖拽全部由这一个 [Animatable] 驱动，所以手势中途松手、
     * 或者在归位动画途中再次按下，都能从当前值继续，不会跳帧。
     */
    val progress = remember { Animatable(1f) }
    var aboutMounted by remember { mutableStateOf(false) }

    BackHandler(enabled = showAbout) { showAbout = false }

    // 打开 / 关闭的统一入口：showAbout 变化即驱动进度
    LaunchedEffect(showAbout) {
        if (showAbout) {
            aboutMounted = true
            progress.snapTo(1f)
            progress.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        } else if (aboutMounted) {
            progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            aboutMounted = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Tokens.Bg)) {
        // 下层主界面：随进度缩小 + 压暗，形成「关于页盖在上面」的层级感。
        // 常驻组合，返回时 Pager 页码与滚动位置都不丢。
        // hierarchicalFocusGroup：关于页打开时把主界面标为非活跃，
        // 表冠焦点只给关于页的列表，避免旋转表冠滚到屏幕背后的主页。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hierarchicalFocusGroup(active = !showAbout)
                .graphicsLayer {
                    val open = 1f - progress.value
                    val scale = 1f - 0.10f * open
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.45f * open
                },
        ) {
            HomeScreen(
                payload = snapshot.payload,
                now = now,
                onAbout = { showAbout = true },
            )
        }

        if (aboutMounted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = progress.value * size.width }
                    .pointerInput(Unit) {
                        val width = size.width.coerceAtLeast(1).toFloat()
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                tracker.resetTracking()
                                // 归位/滑出动画进行中被按下，立刻接管，从当前值继续
                                scope.launch { progress.stop() }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch {
                                    progress.snapTo(
                                        (progress.value + dragAmount / width).coerceIn(0f, 1f)
                                    )
                                }
                            },
                            onDragEnd = {
                                val velocity = tracker.calculateVelocity().x
                                scope.launch {
                                    // 拖过 28% 或右滑速度够快 → 关闭；否则弹回
                                    val close = progress.value > 0.28f || velocity > 900f
                                    if (close) {
                                        progress.animateTo(
                                            1f,
                                            tween(200, easing = FastOutLinearInEasing),
                                        )
                                        showAbout = false
                                    } else {
                                        progress.animateTo(
                                            0f,
                                            spring(dampingRatio = 0.8f, stiffness = 400f),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    progress.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.8f, stiffness = 400f),
                                    )
                                }
                            },
                        )
                    },
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Tokens.Bg)) {
                    AboutScreen(
                        meta = snapshot.meta,
                        versionName = versionName,
                        onBack = { showAbout = false },
                    )
                }
            }
        }

        // 「上次运行崩溃」反馈卡：盖在最上层，读完整堆栈，可复制回传
        lastCrash?.let { crashText ->
            val crashScroll = rememberScrollState()
            Box(modifier = Modifier.fillMaxSize().background(Tokens.Bg)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Label(
                        "上次运行崩溃",
                        TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary),
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(crashScroll)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Tokens.Surface)
                            .padding(8.dp),
                    ) {
                        Label(
                            crashText.take(6000),
                            TextStyle(fontSize = 8.sp, color = Tokens.TextSecondary),
                            maxLines = 500,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillButton("复制", Tokens.Surface, onClick = {
                            clipboard.setText(AnnotatedString(crashText))
                        })
                        Spacer(Modifier.width(10.dp))
                        PillButton("继续", Tokens.FabBlue, onClick = { lastCrash = null })
                    }
                }
            }
        }
    }
}
