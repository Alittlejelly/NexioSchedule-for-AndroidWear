package com.haooz.chedule

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.haooz.chedule.data.SampleSchedule
import com.haooz.chedule.data.SyncMeta
import com.haooz.chedule.sync.WatchSync
import com.haooz.chedule.ui.AboutScreen
import com.haooz.chedule.ui.HomeScreen
import com.haooz.chedule.ui.Tokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalTime

/**
 * 离屏截图：在 JVM 里真实渲染 Compose 界面并落 PNG，
 * 用于在没有手表 / 模拟器的情况下核对与设计稿的一致性。
 *
 * 圆屏 480×480 = 240dp × 240dp @2x，与 Figma 画布 1:1 对应。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w240dp-h240dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(480),
            view.height.coerceAtLeast(480),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        val out = File("build/screenshots/$name.png")
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("[screenshot] ${out.absolutePath} ${bitmap.width}x${bitmap.height}")
    }

    @Test
    fun home_running() {
        val payload = SampleSchedule.build()
        rule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Tokens.Bg)) {
                HomeScreen(
                    payload = payload,
                    now = LocalTime.of(14, 30),
                    onAbout = {},
                )
            }
        }
        capture("home_running")
    }

    @Test
    fun home_soon() {
        val payload = SampleSchedule.build()
        rule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Tokens.Bg)) {
                HomeScreen(
                    payload = payload,
                    now = LocalTime.of(13, 45),
                    onAbout = {},
                )
            }
        }
        capture("home_soon")
    }

    @Test
    fun about() {
        rule.setContent {
            Box(modifier = Modifier.fillMaxSize().background(Tokens.Bg)) {
                AboutScreen(
                    meta = SyncMeta(
                        lastReceivedAt = System.currentTimeMillis(),
                        receiveCount = 3,
                        lastVersion = 4,
                        lastSource = "手机推送",
                    ),
                    connected = true,
                    versionName = "1.0.4",
                    onBack = {},
                )
            }
        }
        capture("about")
    }
}
