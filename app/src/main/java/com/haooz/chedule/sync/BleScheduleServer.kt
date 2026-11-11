package com.haooz.chedule.sync

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID

/**
 * 手表侧蓝牙接收端（BLE 外设）。
 *
 * 角色分工：**手表广播 + 提供 GATT 服务，手机扫描后连上来推整表**。
 * 之所以让手表当外设：手机才是「发起导出」的一方，它需要有设备列表可选。
 *
 * 数据格式：手机把「4 字节大端总长度 + 整表 JSON」按 MTU 分片写入同一特征，
 * 这里累计到长度足够即解析落盘。整表 30~40KB，512 MTU 下约 70 片。
 *
 * 实现细节参照了同类手表应用的实测结论：
 * - 广播主包只放 service UUID，设备名放扫描响应，避免单包超 31 字节
 * - 写回调里必须回 sendResponse（responseNeeded 为 true 时），否则手机会卡在等应答
 */
@SuppressLint("MissingPermission")
object BleScheduleServer {

    private const val TAG = "BleScheduleServer"

    /** 与手机端 BleScheduleSender 保持一致 */
    val SERVICE_UUID: UUID = UUID.fromString("0000ab10-0000-1000-8000-00805f9b34fb")
    val CHAR_UUID: UUID = UUID.fromString("0000ab11-0000-1000-8000-00805f9b34fb")

    /** 防御异常长度：正常整表 30~40KB */
    private const val MAX_PAYLOAD = 4 * 1024 * 1024

    private var manager: BluetoothManager? = null
    private var adapter: android.bluetooth.BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private var buffer: ByteArrayOutputStream? = null
    private var expected = -1
    private var started = false

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            emptyArray()
        }

    fun hasPermissions(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun start(context: Context): Boolean {
        if (started) return true
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = mgr?.adapter
        if (mgr == null || adapter == null) {
            Log.w(TAG, "bluetooth unavailable")
            return false
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "bluetooth disabled")
            return false
        }
        manager = mgr

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        )

        gattServer = runCatching { mgr.openGattServer(context, serverCallback) }.getOrNull()
        if (gattServer == null) {
            Log.w(TAG, "openGattServer failed")
            return false
        }
        gattServer?.addService(service)

        val adv = adapter.bluetoothLeAdvertiser
        if (adv == null) {
            Log.w(TAG, "advertiser unavailable")
            gattServer?.close()
            gattServer = null
            return false
        }
        advertiser = adv
        this.adapter = adapter

        val ok = startAdvertising(adapter, adv)

        started = true
        Log.i(TAG, "server started=$ok")
        return ok
    }

    /**
     * 开始广播。
     *
     * 单独抽出来是因为**连接建立会消耗掉可连接广播**：BLE 规范下设备被连上后广播自动停止，
     * 断开后不会自己恢复。不重新广播的话，手机第二次永远扫不到手表，
     * 表现就是「推过一次之后必须杀掉手表 App 重开才能再推」。
     */
    private fun startAdvertising(
        adapter: android.bluetooth.BluetoothAdapter,
        adv: BluetoothLeAdvertiser,
    ): Boolean {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // 主包只放 service UUID；设备名放扫描响应，避免超 31 字节
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        // 重启广播前先停掉旧的：重复 start 会返回 ALREADY_STARTED(3) 而静默失败
        runCatching { adv.stopAdvertising(advertiseCallback) }

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i(TAG, "advertising as ${adapter.name}")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "advertise failed: $errorCode")
            }
        }
        advertiseCallback = callback

        return runCatching {
            adv.startAdvertising(settings, data, scanResponse, callback)
        }.onFailure { Log.w(TAG, "startAdvertising: ${it.message}") }.isSuccess
    }

    /** 断开后把广播补回来，让手机能再次发现手表 */
    private fun resumeAdvertising() {
        val a = adapter ?: return
        val adv = advertiser ?: return
        if (!started) return
        runCatching { startAdvertising(a, adv) }
            .onFailure { Log.w(TAG, "resumeAdvertising: ${it.message}") }
    }

    fun stop() {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        runCatching { gattServer?.close() }
        advertiseCallback = null
        gattServer = null
        advertiser = null
        adapter = null
        resetBuffer()
        started = false
    }

    private fun resetBuffer() {
        buffer = null
        expected = -1
    }

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: android.bluetooth.BluetoothDevice?, status: Int, newState: Int) {
            val connected = newState == BluetoothProfile.STATE_CONNECTED
            Log.i(TAG, "peer ${device?.address} -> ${if (connected) "connected" else "disconnected"}")
            WatchSync.markConnected(connected)
            if (!connected) {
                resetBuffer()
                // 连接会消耗掉可连接广播，断开后必须补回来，否则手机第二次扫不到手表
                resumeAdvertising()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: android.bluetooth.BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic?.uuid == CHAR_UUID && value != null) {
                appendChunk(value)
            }
            if (responseNeeded) {
                runCatching {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value ?: byteArrayOf(),
                    )
                }
            }
        }
    }

    /** 首片带 4 字节长度头，其余为裸数据；累计够长即完成 */
    private fun appendChunk(chunk: ByteArray) {
        if (expected < 0) {
            if (chunk.size < 4) return
            val total = ByteBuffer.wrap(chunk, 0, 4).int
            if (total <= 0 || total > MAX_PAYLOAD) {
                Log.w(TAG, "bad payload length: $total")
                resetBuffer()
                return
            }
            expected = total
            buffer = ByteArrayOutputStream(total).apply { write(chunk, 4, chunk.size - 4) }
        } else {
            buffer?.write(chunk)
        }

        val stream = buffer ?: return
        if (stream.size() >= expected) {
            val json = String(stream.toByteArray(), Charsets.UTF_8)
            resetBuffer()
            Log.i(TAG, "payload received, ${json.length} chars")
            WatchSync.onPayload(json, "蓝牙推送")
        }
    }
}
