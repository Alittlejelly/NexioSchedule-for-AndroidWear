package com.haooz.chedule.sync

import android.content.Context
import com.haooz.chedule.data.ScheduleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 手表端 ↔ 手机端 课表同步状态。
 *
 * 传输由 [BleScheduleServer] 负责：手表作为 BLE 外设广播，手机扫描后连接并推整表。
 * 这里只维护「当前是否收到/连接」的展示状态，并承接落盘。
 *
 * 之所以不用 Wear OS Data Layer：国行小米手表 5 没有 GMS，也没有 Wear OS 配对体系，
 * `connectedNodes` 恒为空，那条通道在这台设备上一条消息也发不出去。
 */
object WatchSync {

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun init(context: Context) {
        ScheduleStore.init(context)
    }

    /** 收到一整份 payload 后落盘；成功才算连接可用 */
    fun onPayload(json: String, source: String) {
        if (ScheduleStore.onPayloadReceived(json, source)) {
            _connected.value = true
        }
    }

    /** GATT 连接状态变化时更新（断开后不立刻清缓存，界面继续显示上次课表） */
    fun markConnected(value: Boolean) {
        _connected.value = value
    }
}
