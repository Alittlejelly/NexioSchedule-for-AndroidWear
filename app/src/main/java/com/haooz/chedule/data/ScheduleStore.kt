package com.haooz.chedule.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/** 同步元信息：关于页的「同步诊断」直接读这里 */
data class SyncMeta(
    val lastReceivedAt: Long = 0L,
    val receiveCount: Int = 0,
    val lastVersion: Int = 0,
    val lastSource: String = "",
    val lastError: String = "",
)

data class StoreSnapshot(
    val payload: SchedulePayload? = null,
    val meta: SyncMeta = SyncMeta(),
)

/**
 * 课表本地仓库。
 *
 * 行为对齐手环版：整表 JSON 落盘缓存，冷启动 / 断开手机后仍可查看；
 * 收到新 payload 即覆盖并广播给 UI。
 */
object ScheduleStore {

    private const val FILE_PAYLOAD = "nexio-schedule.json"
    private const val FILE_META = "nexio-sync-meta.json"

    private var appContext: Context? = null

    private val _state = MutableStateFlow(StoreSnapshot())
    val state: StateFlow<StoreSnapshot> = _state

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx

        val cached = readFile(FILE_PAYLOAD)?.let { SchedulePayload.parse(it) }
        val meta = readMeta()
        // 不再内置示例课表：没同步过就是空，主屏显示「尚未同步课表」，
        // 避免用户把示例课程误当成自己的课表。
        _state.value = StoreSnapshot(payload = cached, meta = meta)
    }

    /** 收到手机端整表；返回是否被接受 */
    @Synchronized
    fun onPayloadReceived(json: String, source: String): Boolean {
        val payload = SchedulePayload.parse(json)
        if (payload == null) {
            updateMeta { it.copy(lastError = "协议不匹配或版本过低", lastSource = source) }
            return false
        }
        writeFile(FILE_PAYLOAD, json)
        val meta = updateMeta {
            it.copy(
                lastReceivedAt = System.currentTimeMillis(),
                receiveCount = it.receiveCount + 1,
                lastVersion = payload.version,
                lastSource = source,
                lastError = "",
            )
        }
        _state.value = StoreSnapshot(payload = payload, meta = meta)
        return true
    }

    /** 同步失败记录（用于关于页诊断） */
    fun noteSyncError(message: String) {
        updateMeta { it.copy(lastError = message) }
    }

    private fun updateMeta(block: (SyncMeta) -> SyncMeta): SyncMeta {
        val next = block(_state.value.meta)
        _state.value = _state.value.copy(meta = next)
        runCatching {
            val json = JSONObject()
                .put("lastReceivedAt", next.lastReceivedAt)
                .put("receiveCount", next.receiveCount)
                .put("lastVersion", next.lastVersion)
                .put("lastSource", next.lastSource)
                .put("lastError", next.lastError)
                .toString()
            writeFile(FILE_META, json)
        }
        return next
    }

    private fun readMeta(): SyncMeta = runCatching {
        val text = readFile(FILE_META) ?: return SyncMeta()
        val o = JSONObject(text)
        SyncMeta(
            lastReceivedAt = o.optLong("lastReceivedAt", 0L),
            receiveCount = o.optInt("receiveCount", 0),
            lastVersion = o.optInt("lastVersion", 0),
            lastSource = o.optString("lastSource", ""),
            lastError = o.optString("lastError", ""),
        )
    }.getOrDefault(SyncMeta())

    private fun fileOf(name: String): File? = appContext?.let { File(it.filesDir, name) }

    private fun readFile(name: String): String? = runCatching {
        val f = fileOf(name) ?: return null
        if (!f.exists()) null else f.readText(Charsets.UTF_8)
    }.getOrNull()

    private fun writeFile(name: String, text: String) {
        runCatching { fileOf(name)?.writeText(text, Charsets.UTF_8) }
    }
}

