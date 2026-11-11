package com.haooz.chedule

import android.app.Application
import android.util.Log
import java.io.File

/**
 * 崩溃日志落盘。
 *
 * 小米手表 5 这类设备不方便常驻 logcat，闪退后拿不到堆栈。
 * 这里把未捕获异常写进应用外部目录，出问题时可从文件里直接读到完整堆栈：
 *   /sdcard/Android/data/com.haooz.chedule/files/crash-<时间戳>.txt
 */
class CrashApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val dir = getExternalFilesDir(null) ?: filesDir
                val file = File(dir, "crash-${System.currentTimeMillis()}.txt")
                file.writeText(
                    buildString {
                        appendLine("time=${java.util.Date()}")
                        appendLine("thread=${thread.name}")
                        appendLine(Log.getStackTraceString(error))
                    },
                    Charsets.UTF_8,
                )
                Log.e("CrashApplication", "crash saved -> ${file.absolutePath}", error)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
