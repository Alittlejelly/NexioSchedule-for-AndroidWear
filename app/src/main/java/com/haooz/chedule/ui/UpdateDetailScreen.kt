package com.haooz.chedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.TimeText
import com.haooz.chedule.R

/**
 * 「版本更新」详情页（画板 32）：独立的全屏页面，从关于页的更新卡片进入。
 * 有新版本时显示新版本日志 + 下载/安装；已是最新时显示当前安装版本的更新日志。
 * 状态来自 [UpdateSession]，与关于页的更新卡片共享，页面切换不打断下载。
 */
@Composable
fun UpdateDetailScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 读取 Snapshot 状态即建立订阅，UpdateSession 里任何变化都会触发重组
    val ui = UpdateSession.ui
    BackHandler { onBack() }

    // 进入页面：先读上次检查的本地缓存（没点过检查更新也有日志可看），
    // 然后自动检测一次更新，结果回来即刷新页面
    LaunchedEffect(Unit) {
        UpdateSession.loadCache(context)
        if (UpdateSession.ui == UpdateUi.Idle) UpdateSession.check(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 让开顶部曲面时间
            Spacer(Modifier.height(46.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_caret_left),
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { onBack() }
                        .padding(6.dp),
                )
                Spacer(Modifier.width(2.dp))
                Label(
                    "版本更新",
                    TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary),
                )
            }
            Spacer(Modifier.height(14.dp))

            // 有新版本 → 新版本日志；否则依次取 本次检查结果 / 本地缓存（当前版本日志）
            val hasUpdate = ui is UpdateUi.HasUpdate || ui is UpdateUi.Downloading || ui is UpdateUi.Ready
            val info = (ui as? UpdateUi.HasUpdate)?.info
                ?: UpdateSession.currentLog
                ?: UpdateSession.cachedLog
            if (info == null && ui == UpdateUi.Checking) {
                // 无缓存且正在检测时的过渡提示
                Label(
                    "正在检查更新…",
                    TextStyle(fontSize = 12.sp, color = Tokens.TextSecondary),
                )
            }
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
                    UpdatePill("安装", onClick = { UpdateSession.install(context, s.file) })
                }
                is UpdateUi.HasUpdate -> {
                    UpdatePill("下载并安装", onClick = { UpdateSession.download(context, s.info) })
                }
                else -> {}
            }
            Spacer(Modifier.height(30.dp))
        }

        // 贴合屏幕上边缘的曲面时间
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}
