package com.haooz.chedule

import android.app.Application
import android.util.Log

/**
 * 崩溃日志落盘。
 *
 * 小米手表 5 这类设备不方便常驻 logcat，闪退后拿不到堆栈。
 * 崩溃信息由 [CrashReporter] 写进应用私有目录，下次启动在首页弹
 * 「上次运行崩溃」反馈卡，用户复制内容即可回传完整堆栈。
 */
class CrashApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            CrashReporter.write(this, error, thread)
            Log.e("CrashApplication", "crash saved", error)
            previous?.uncaughtException(thread, error)
        }
    }
}
