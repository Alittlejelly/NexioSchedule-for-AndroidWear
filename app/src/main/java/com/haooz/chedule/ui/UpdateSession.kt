package com.haooz.chedule.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.update.AppUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** 版本更新的界面状态 */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class HasUpdate(val info: AppUpdate.Info) : UpdateUi
    data object UpToDate : UpdateUi
    data class Downloading(val progress: Int) : UpdateUi
    data class Ready(val file: File) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

// 配色取自设计稿（画板 29/32）：卡片黑→蓝渐变，按钮深蓝渐变
val UpdateCardTop = Color(0xFF3381FF)
val UpdateCardBottom = Color.Black
val UpdatePillTop = Color(0xFF175EC6)
val UpdatePillBottom = Color(0xFF1C4D93)

/** 蓝渐变胶囊按钮（设计稿「检查更新」样式），更新卡片与详情页共用 */
@Composable
fun UpdatePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
 * 更新流程的会话状态：关于页的更新卡片与独立的「版本更新」页读同一份数据，
 * 检查/下载在哪个页面发起都行，页面切换不会打断进行中的下载。
 */
object UpdateSession {

    var ui: UpdateUi by mutableStateOf(UpdateUi.Idle)
        private set

    /** 已是最新时记录远程 Info：update.json 的 notes 即当前安装版本的更新日志 */
    var currentLog: AppUpdate.Info? by mutableStateOf(null)
        private set

    /** 上次检查结果的本地缓存：没点过「检查更新」时详情页也能展示日志 */
    var cachedLog: AppUpdate.Info? by mutableStateOf(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var promptedFile: File? = null
    private var cacheLoaded = false

    /**
     * 读取上次检查的缓存（filesDir/update_cache.json）。
     * 在详情页进入时调用，保证「从没点过检查更新」也有日志可看。
     */
    fun loadCache(context: Context) {
        if (cacheLoaded) return
        cacheLoaded = true
        runCatching {
            val file = File(context.filesDir, "update_cache.json")
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            cachedLog = AppUpdate.Info(
                o.optLong("versionCode", 0L),
                o.optString("versionName", ""),
                "", // 缓存只用于展示日志，不携带下载路径
                o.optString("notes", ""),
                o.optString("source", ""),
            )
        }
    }

    private fun saveCache(context: Context, info: AppUpdate.Info) {
        runCatching {
            File(context.filesDir, "update_cache.json").writeText(
                JSONObject()
                    .put("versionCode", info.versionCode)
                    .put("versionName", info.versionName)
                    .put("notes", info.notes)
                    .put("source", info.source)
                    .toString(),
            )
        }
    }

    /** 检查更新：Gitee 优先、GitHub 兜底 */
    fun check(context: Context) {
        ui = UpdateUi.Checking
        scope.launch {
            ui = withContext(Dispatchers.IO) {
                AppUpdate.fetchLatest(context).fold(
                    onSuccess = { info ->
                        // 缓存本次结果：无论有无新版本，notes 都是远程最新一版的日志
                        cachedLog = info
                        saveCache(context, info)
                        if (info.versionCode > AppUpdate.currentVersionCode(context)) {
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

    /** 下载 APK（带进度），完成后自动唤起一次安装器 */
    fun download(context: Context, info: AppUpdate.Info) {
        ui = UpdateUi.Downloading(0)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AppUpdate.downloadApk(context, info) { progress ->
                    ui = UpdateUi.Downloading(progress)
                }
            }
            ui = result.fold(
                onSuccess = { file ->
                    install(context, file)
                    UpdateUi.Ready(file)
                },
                onFailure = { UpdateUi.Failed(it.message ?: "下载失败") },
            )
        }
    }

    /** 唤起系统安装器（下载完成后自动调用一次，「安装」按钮可重试） */
    fun install(context: Context, file: File) {
        promptedFile = file
        runCatching { AppUpdate.install(context, file) }
    }
}
