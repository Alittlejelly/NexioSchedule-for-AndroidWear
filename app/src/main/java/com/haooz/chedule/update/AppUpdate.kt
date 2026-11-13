package com.haooz.chedule.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 在线更新（GitHub + Gitee 双源）。
 *
 * 发布流程：把新版 APK 放进仓库 `apk/` 目录、更新根目录 `update.json`，推送即可：
 * ```json
 * {"versionCode": 101, "versionName": "1.0.1 Wear",
 *  "apkPath": "apk/Nexio-watch-1.0.1-Wear-101.apk", "notes": "..."}
 * ```
 * 检查：依次请求 Gitee / GitHub 的 raw 地址读 update.json，versionCode 大于本机即有更新。
 * 下载：与检查同源（哪个源成功用哪个），逐字节落盘并回报进度。
 * 安装：FileProvider + ACTION_VIEW 唤起系统安装器（需允许安装未知应用）。
 */
object AppUpdate {

    const val OWNER = "Alittlejelly"
    const val REPO = "NexioSchedule-for-AndroidWear"

    /** 检查/下载源，顺序即优先级：国内 Gitee 优先，GitHub 兜底 */
    private val RAW_BASES = listOf(
        "https://gitee.com/$OWNER/$REPO/raw/main/" to "Gitee",
        "https://raw.githubusercontent.com/$OWNER/$REPO/main/" to "GitHub",
    )

    data class Info(
        val versionCode: Long,
        val versionName: String,
        val apkPath: String,
        val notes: String,
        val source: String,
    )

    /** 本机已安装的 versionCode */
    fun currentVersionCode(context: Context): Long = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi)
    }.getOrDefault(0L)

    fun currentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** 拉取 update.json；没有比本机更新的版本时返回 null。异常时返回 Error */
    fun fetchLatest(context: Context): Result<Info?> {
        val current = currentVersionCode(context)
        for ((base, source) in RAW_BASES) {
            try {
                val text = httpGet(base + "update.json") ?: continue
                val o = JSONObject(text)
                val code = o.optLong("versionCode", 0L)
                if (code <= 0) continue
                val apkPath = o.optString("apkPath", "")
                if (apkPath.isBlank()) continue
                return Result.success(
                    if (code > current) {
                        Info(code, o.optString("versionName", ""), apkPath, o.optString("notes", ""), source)
                    } else {
                        null // 已是最新
                    }
                )
            } catch (_: Exception) {
                // 换下一个源
            }
        }
        return Result.failure(Exception("无法连接 Gitee / GitHub，请检查手表网络"))
    }

    /** 下载 APK 到应用外部缓存目录，回调 0..100 进度 */
    fun downloadApk(context: Context, info: Info, onProgress: (Int) -> Unit): Result<File> {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "update")
        dir.mkdirs()
        val target = File(dir, info.apkPath.substringAfterLast('/'))
        for ((base, _) in RAW_BASES) {
            try {
                val conn = open(base + info.apkPath)
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    val tmp = File(dir, target.name + ".part")
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                    // 完整性：有总长度时校验，不对就当失败换源
                    if (total > 0 && tmp.length() != total.toLong()) {
                        tmp.delete()
                        throw IllegalStateException("下载不完整")
                    }
                    if (!tmp.renameTo(target)) {
                        target.delete()
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                }
                conn.disconnect()
                return Result.success(target)
            } catch (e: Exception) {
                target.delete()
                File(dir, target.name + ".part").delete()
                lastError = e.message ?: "下载失败"
            }
        }
        return Result.failure(Exception(lastError ?: "下载失败"))
    }

    private var lastError: String? = null

    /** 唤起系统安装器 */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        context.startActivity(intent)
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "NexioWear/${AppUpdate::class.simpleName}")
        return conn
    }

    private fun httpGet(url: String): String? = try {
        val conn = open(url)
        val text = if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
        conn.disconnect()
        text
    } catch (_: Exception) {
        null
    }
}
