package com.haooz.chedule

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志落盘 + 读取（结构仿照 mgaide_wear 工程）。
 *
 * 小米手表 5 不方便常驻 logcat，闪退后拿不到堆栈。闪退时把机型/系统/堆栈
 * 写进 filesDir/last_crash.txt，下次启动弹出「上次运行崩溃」反馈卡，
 * 用户一键复制整段内容发出来即可定位问题。
 */
object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"

    @Synchronized
    fun write(context: Context, throwable: Throwable, thread: Thread? = null) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)

            pw.println("=== Nexio Wear Crash ===")
            pw.println("time=${formatNow()}")
            pw.println("thread=${thread?.name ?: "unknown"}")
            pw.println("sdk=${Build.VERSION.SDK_INT}")
            pw.println("release=${Build.VERSION.RELEASE}")
            pw.println("brand=${Build.BRAND}")
            pw.println("model=${Build.MODEL}")
            pw.println("device=${Build.DEVICE}")
            pw.println("version=${runCatching { versionName(context) }.getOrDefault("?")}")
            pw.println()

            throwable.printStackTrace(pw)
            pw.flush()

            File(context.filesDir, FILE_NAME).writeText(sw.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {
            // 记录崩溃自身绝不能再崩
        }
    }

    /**
     * 读出并删除崩溃记录。
     * 兼容旧版本落在外部目录的 crash-*.txt：首次读取时迁移成 last_crash.txt，
     * 这样覆盖安装（保留应用数据）后，旧版本的崩溃也能被反馈到。
     */
    @Synchronized
    fun consume(context: Context): String? = try {
        val target = File(context.filesDir, FILE_NAME)
        if (!target.exists()) migrateLegacy(context, target)
        if (!target.exists()) {
            null
        } else {
            target.readText(Charsets.UTF_8).also { target.delete() }
        }
    } catch (_: Throwable) {
        null
    }

    private fun migrateLegacy(context: Context, target: File) {
        // 旧版处理器的落盘位置：优先外部目录，拿不到外部目录时落在 filesDir，两处都扫
        val dirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
        val legacy = dirs.asSequence()
            .mapNotNull { dir ->
                dir.listFiles { f -> f.name.startsWith("crash-") }?.maxByOrNull { it.lastModified() }
            }
            .maxByOrNull { it.lastModified() } ?: return
        runCatching {
            target.writeText(legacy.readText(Charsets.UTF_8), Charsets.UTF_8)
            legacy.delete()
        }
    }

    private fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    private fun formatNow(): String = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    } catch (_: Throwable) {
        Date().toString()
    }
}
